package com.mganji.ganji.service;

import com.mganji.ganji.dto.SmsEvidenceRequest;
import com.mganji.ganji.exception.InvalidActionRequestException;
import com.mganji.ganji.model.ActionType;
import com.mganji.ganji.model.Verification;
import com.mganji.ganji.model.WalletAction;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Decides whether an M-Pesa message read on the customer's phone belongs to a request. Pure logic, no database.
 * <p>
 * It can say no loudly (wrong amount, wrong number, wrong time) and it can say yes at two strengths. It can never
 * say "Safaricom confirmed", because the message comes from the customer's phone and could be edited or faked there.
 */
public final class SmsMatcher {

    /** The message may arrive a little before our clock says the request was approved. */
    static final Duration EARLIER = Duration.ofMinutes(2);
    /** Phone clocks drift. */
    static final Duration LATER = Duration.ofMinutes(5);

    private static final Map<ActionType, Set<SmsEvidenceRequest.Kind>> KINDS = Map.of(
            ActionType.SEND_MONEY, Set.of(SmsEvidenceRequest.Kind.SENT),
            ActionType.PAY_POCHI, Set.of(SmsEvidenceRequest.Kind.SENT, SmsEvidenceRequest.Kind.PAID),
            ActionType.PAY_TILL, Set.of(SmsEvidenceRequest.Kind.PAID),
            ActionType.PAY_BILL, Set.of(SmsEvidenceRequest.Kind.BILL),
            ActionType.BUY_AIRTIME, Set.of(SmsEvidenceRequest.Kind.AIRTIME));

    private SmsMatcher() {
    }

    public static Verification match(WalletAction action, SmsEvidenceRequest sms, Instant now) {
        Set<SmsEvidenceRequest.Kind> allowed = KINDS.get(action.getType());
        if (allowed == null) {
            throw new InvalidActionRequestException("A Ratiba is set up, not paid straight away, so there is no payment message to match");
        }
        if (sms.amount() != action.getAmount()) {
            throw new InvalidActionRequestException("The M-Pesa message is for KES " + sms.amount()
                    + " but this request is for KES " + action.getAmount() + ". It was not matched.");
        }
        if (!allowed.contains(sms.kind())) {
            throw new InvalidActionRequestException("The M-Pesa message is a different kind of transaction from this request. It was not matched.");
        }
        Instant since = (action.getApprovedAt() == null ? action.getCreatedAt() : action.getApprovedAt()).minus(EARLIER);
        Instant at = Instant.ofEpochMilli(sms.smsAt());
        if (at.isBefore(since) || at.isAfter(now.plus(LATER))) {
            throw new InvalidActionRequestException("That M-Pesa message is not from around the time of this request. It was not matched.");
        }

        Map<String, String> d = action.getDetails();
        return switch (action.getType()) {
            case SEND_MONEY -> byNumber(d.get("recipientPhone"), sms.payee());
            case PAY_POCHI -> byNumber(d.get("pochiPhone"), sms.payee());
            case BUY_AIRTIME -> byNumber(d.get("airtimePhone"), sms.payee());
            case PAY_BILL -> byAccount(d.get("account"), sms.account());
            default -> Verification.SMS_AMOUNT;
        };
    }

    /** Matches on the last nine digits, so 0712345678 and 254712345678 agree. */
    private static Verification byNumber(String expected, String payee) {
        if (expected == null || payee == null) {
            return Verification.SMS_AMOUNT;
        }
        String seen = lastNine(payee);
        if (seen == null) {
            return Verification.SMS_AMOUNT;
        }
        if (!seen.equals(lastNine(expected))) {
            throw new InvalidActionRequestException("The M-Pesa message shows a different number from this request. It was not matched. "
                    + "If money may have gone to the wrong number, tell the assistant right away.");
        }
        return Verification.SMS_MATCHED;
    }

    private static Verification byAccount(String expected, String account) {
        if (expected == null || account == null || account.isBlank()) {
            return Verification.SMS_AMOUNT;
        }
        if (!squash(account).equals(squash(expected))) {
            throw new InvalidActionRequestException("The M-Pesa message shows a different account from this request. It was not matched. "
                    + "If money may have gone to the wrong account, tell the assistant right away.");
        }
        return Verification.SMS_MATCHED;
    }

    /** The last nine digits of the first run of at least nine digits in the text, or null when there is none. */
    static String lastNine(String text) {
        var m = java.util.regex.Pattern.compile("\\d[\\d ]{7,}\\d").matcher(text);
        while (m.find()) {
            String digits = m.group().replace(" ", "");
            if (digits.length() >= 9) {
                return digits.substring(digits.length() - 9);
            }
        }
        return null;
    }

    private static String squash(String s) {
        return s.replaceAll("[^\\p{L}\\p{N}]", "").toUpperCase(Locale.ENGLISH);
    }
}
