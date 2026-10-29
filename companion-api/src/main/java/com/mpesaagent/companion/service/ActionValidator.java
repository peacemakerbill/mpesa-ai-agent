package com.mpesaagent.companion.service;

import com.mpesaagent.companion.config.CompanionProperties;
import com.mpesaagent.companion.exception.InvalidActionRequestException;
import com.mpesaagent.companion.model.ActionType;
import com.mpesaagent.companion.util.Phones;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks what the agent (and so, indirectly, an image or a voice note) asked for, and keeps only the fields
 * that make sense for that kind of request. Nothing outside the allowed lists reaches the phone screen.
 */
@Component
public class ActionValidator {

    public record Validated(int amount, Map<String, String> details, String label) {
    }

    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");
    private static final Pattern SHORTCODE = Pattern.compile("^\\d{5,7}$");
    private static final Pattern SAFE_TEXT = Pattern.compile("^[\\p{L}\\p{N} .,_\\-/#&'()@:+]+$");
    private static final Set<String> FREQUENCIES = Set.of("DAILY", "WEEKLY", "MONTHLY");
    private static final Set<ActionType> RATIBA_TARGETS = Set.of(ActionType.SEND_MONEY, ActionType.PAY_BILL, ActionType.PAY_TILL);

    private final CompanionProperties props;
    private final Clock clock;

    public ActionValidator(CompanionProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    public Validated validate(String ownerPhone, ActionType type, Integer amount, Map<String, String> raw, String label) {
        if (amount == null || amount < 1) {
            throw new InvalidActionRequestException("The amount must be at least KES 1");
        }
        if (amount > props.maxAmount()) {
            throw new InvalidActionRequestException("The amount is above the limit of " + ActionSummary.kes(props.maxAmount()));
        }
        Map<String, String> in = raw == null ? Map.of() : raw;
        Map<String, String> out = new LinkedHashMap<>();

        switch (type) {
            case SEND_MONEY -> sendMoney(ownerPhone, in, out);
            case PAY_BILL -> payBill(in, out);
            case PAY_TILL -> payTill(in, out);
            case PAY_POCHI -> pochi(ownerPhone, in, out);
            case BUY_AIRTIME -> {
                if (amount < props.minAirtime()) {
                    throw new InvalidActionRequestException("The smallest airtime purchase is " + ActionSummary.kes(props.minAirtime()));
                }
                airtime(in, out);
            }
            case SCHEDULE_RATIBA -> ratiba(ownerPhone, in, out);
        }
        rejectUnknownKeys(type, in);
        return new Validated(amount, out, label == null || label.isBlank() ? null : text("label", label, 80));
    }

    private void sendMoney(String owner, Map<String, String> in, Map<String, String> out) {
        boolean hasPhone = present(in, "recipientPhone");
        boolean hasContact = present(in, "contactName");
        if (hasPhone == hasContact) {
            throw new InvalidActionRequestException("Give either the recipient's phone number or a contact name, not both and not neither");
        }
        if (hasPhone) {
            String phone = Phones.normalize(in.get("recipientPhone"));
            if (phone.equals(owner)) {
                throw new InvalidActionRequestException("That is the customer's own number");
            }
            out.put("recipientPhone", phone);
        } else {
            out.put("contactName", text("contactName", in.get("contactName"), 60));
        }
        optionalText(in, out, "recipientName");
    }

    private void payBill(Map<String, String> in, Map<String, String> out) {
        out.put("paybill", shortcode("paybill", in.get("paybill")));
        out.put("account", text("account", required(in, "account"), 40));
        optionalText(in, out, "businessName");
    }

    private void payTill(Map<String, String> in, Map<String, String> out) {
        out.put("till", shortcode("till", in.get("till")));
        optionalText(in, out, "businessName");
    }

    private void pochi(String owner, Map<String, String> in, Map<String, String> out) {
        String phone = Phones.normalize(required(in, "pochiPhone"));
        if (phone.equals(owner)) {
            throw new InvalidActionRequestException("That is the customer's own number");
        }
        out.put("pochiPhone", phone);
        optionalText(in, out, "businessName");
    }

    private void airtime(Map<String, String> in, Map<String, String> out) {
        if (present(in, "airtimePhone")) {
            out.put("airtimePhone", Phones.normalize(in.get("airtimePhone")));
        }
    }

    private void ratiba(String owner, Map<String, String> in, Map<String, String> out) {
        ActionType target;
        try {
            target = ActionType.valueOf(required(in, "target").trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidActionRequestException("A Ratiba can pay a person, a Paybill or a Till");
        }
        if (!RATIBA_TARGETS.contains(target)) {
            throw new InvalidActionRequestException("A Ratiba can pay a person, a Paybill or a Till");
        }
        out.put("target", target.name());
        String frequency = required(in, "frequency").trim().toUpperCase();
        if (!FREQUENCIES.contains(frequency)) {
            throw new InvalidActionRequestException("The frequency must be DAILY, WEEKLY or MONTHLY");
        }
        out.put("frequency", frequency);

        LocalDate today = LocalDate.now(clock.withZone(NAIROBI));
        LocalDate start = date("startDate", required(in, "startDate"));
        if (start.isBefore(today) || start.isAfter(today.plusYears(1))) {
            throw new InvalidActionRequestException("The start date must be within the next year");
        }
        out.put("startDate", start.toString());
        if (present(in, "endDate")) {
            LocalDate end = date("endDate", in.get("endDate"));
            if (end.isBefore(start) || end.isAfter(start.plusYears(5))) {
                throw new InvalidActionRequestException("The end date must be after the start date and within five years");
            }
            out.put("endDate", end.toString());
        }
        switch (target) {
            case SEND_MONEY -> sendMoney(owner, in, out);
            case PAY_BILL -> payBill(in, out);
            default -> payTill(in, out);
        }
    }

    /** A key that this kind of request does not use is a sign of a confused or manipulated request. */
    private void rejectUnknownKeys(ActionType type, Map<String, String> in) {
        Set<String> allowed = switch (type) {
            case SEND_MONEY -> Set.of("recipientPhone", "contactName", "recipientName");
            case PAY_BILL -> Set.of("paybill", "account", "businessName");
            case PAY_TILL -> Set.of("till", "businessName");
            case PAY_POCHI -> Set.of("pochiPhone", "businessName");
            case BUY_AIRTIME -> Set.of("airtimePhone");
            case SCHEDULE_RATIBA -> Set.of("target", "frequency", "startDate", "endDate", "recipientPhone", "contactName",
                    "recipientName", "paybill", "account", "till", "businessName");
        };
        for (String key : in.keySet()) {
            if (!allowed.contains(key)) {
                throw new InvalidActionRequestException("Unexpected detail: " + (key.length() > 30 ? key.substring(0, 30) : key));
            }
        }
    }

    private static boolean present(Map<String, String> in, String key) {
        String v = in.get(key);
        return v != null && !v.isBlank();
    }

    private static String required(Map<String, String> in, String key) {
        if (!present(in, key)) {
            throw new InvalidActionRequestException("Missing detail: " + key);
        }
        return in.get(key);
    }

    private static String shortcode(String name, String value) {
        String v = value == null ? "" : value.replaceAll("\\s", "");
        if (!SHORTCODE.matcher(v).matches()) {
            throw new InvalidActionRequestException("The " + name + " number must be 5 to 7 digits");
        }
        return v;
    }

    private static void optionalText(Map<String, String> in, Map<String, String> out, String key) {
        if (present(in, key)) {
            out.put(key, text(key, in.get(key), 60));
        }
    }

    /** Strips control characters, then allows letters, digits and a short list of punctuation only. */
    static String text(String name, String value, int max) {
        String v = value == null ? "" : value.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").strip();
        if (v.isEmpty() || v.length() > max || !SAFE_TEXT.matcher(v).matches()) {
            throw new InvalidActionRequestException("The " + name + " has characters or a length that is not allowed");
        }
        return v;
    }

    private static LocalDate date(String name, String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidActionRequestException("The " + name + " must look like 2026-12-31");
        }
    }
}
