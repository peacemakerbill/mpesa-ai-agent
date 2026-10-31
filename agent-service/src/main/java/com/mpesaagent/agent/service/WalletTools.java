package com.mpesaagent.agent.service;

import com.mpesaagent.agent.client.CompanionClient;
import com.mpesaagent.agent.chat.ChatChannel.Button;
import com.mpesaagent.agent.config.ServiceProperties;
import com.mpesaagent.agent.dto.WalletActionView;
import com.mpesaagent.agent.service.ToolExecutor.Result;
import com.mpesaagent.agent.service.ToolExecutor.ToolException;
import com.mpesaagent.agent.util.Money;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.mpesaagent.agent.service.ToolCatalog.*;

/**
 * The tools that act on the customer's own M-Pesa. None of them moves money and none of them sees a PIN:
 * each one only asks companion-api to store a request, then queues a message with a link. The customer opens
 * the link in the companion app, approves it with biometrics or the phone lock, and finishes in M-Pesa.
 * The phone number always comes from the signed-in customer in ToolContext, never from the model.
 */
@Component
public class WalletTools {

    private static final Set<String> NAMES = Set.of(PREPARE_SEND_MONEY, PREPARE_PAY_BILL, PREPARE_PAY_TILL, PREPARE_PAY_POCHI,
            PREPARE_BUY_AIRTIME, PREPARE_RATIBA, LIST_WALLET_REQUESTS, CANCEL_WALLET_REQUEST, RECORD_CONFIRMATION, FIND_CONTACT);

    /** From least to most likely to contain a mistake. The higher of the two claims wins. */
    private static final List<String> RISK = List.of("TEXT", "CONTACT", "VOICE", "DOCUMENT", "IMAGE");
    private static final Pattern RECEIPT = Pattern.compile("^[A-Z0-9]{8,12}$");

    private final CompanionClient companion;
    private final ServiceProperties.Companion companionProps;

    public WalletTools(CompanionClient companion, ServiceProperties.Companion companionProps) {
        this.companion = companion;
        this.companionProps = companionProps;
    }

    public boolean handles(String name) {
        return NAMES.contains(name);
    }

    public Result execute(ToolContext ctx, String name, JsonNode input) {
        return switch (name) {
            case PREPARE_SEND_MONEY -> prepare(ctx, input, "SEND_MONEY", Map.of(
                    "recipientPhone", "recipient_phone", "contactName", "contact_name", "recipientName", "recipient_name"));
            case PREPARE_PAY_BILL -> prepare(ctx, input, "PAY_BILL", Map.of(
                    "paybill", "paybill", "account", "account", "businessName", "business_name"));
            case PREPARE_PAY_TILL -> prepare(ctx, input, "PAY_TILL", Map.of(
                    "till", "till", "businessName", "business_name"));
            case PREPARE_PAY_POCHI -> prepare(ctx, input, "PAY_POCHI", Map.of(
                    "pochiPhone", "pochi_phone", "businessName", "business_name"));
            case PREPARE_BUY_AIRTIME -> prepare(ctx, input, "BUY_AIRTIME", Map.of("airtimePhone", "for_phone"));
            case PREPARE_RATIBA -> prepare(ctx, input, "SCHEDULE_RATIBA", Map.ofEntries(
                    Map.entry("target", "target"), Map.entry("frequency", "frequency"), Map.entry("startDate", "start_date"),
                    Map.entry("endDate", "end_date"), Map.entry("recipientPhone", "recipient_phone"),
                    Map.entry("contactName", "contact_name"), Map.entry("recipientName", "recipient_name"),
                    Map.entry("paybill", "paybill"), Map.entry("account", "account"), Map.entry("till", "till"),
                    Map.entry("businessName", "business_name")));
            case LIST_WALLET_REQUESTS -> list(ctx, input);
            case CANCEL_WALLET_REQUEST -> cancel(ctx, input);
            case RECORD_CONFIRMATION -> recordConfirmation(ctx, input);
            case FIND_CONTACT -> findContact(ctx, input);
            default -> Result.error("Unknown tool " + name);
        };
    }

    // ------------------------------------------------------------ prepare

    /**
     * @param fields companion-api field name to the tool input name. Empty values are left out and companion-api
     *               decides what is valid, so the rules live in one place.
     */
    private Result prepare(ToolContext ctx, JsonNode input, String type, Map<String, String> fields) {
        int amount = ToolExecutor.positiveInt(input, "amount");
        Map<String, String> details = new LinkedHashMap<>();
        fields.forEach((apiName, toolName) -> {
            String value = ToolExecutor.text(input, toolName, 80);
            if (!value.isEmpty()) {
                details.put(apiName, value);
            }
        });
        String source = riskier(ctx.source(), ToolExecutor.text(input, "details_from", 12));
        String note = ToolExecutor.text(input, "note", 80);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("idempotencyKey", idempotencyKey(ctx.messageId(), type, amount, details));
        body.put("phone", ctx.phone());
        body.put("type", type);
        body.put("amount", amount);
        body.put("details", details);
        body.put("source", source);
        if (!note.isEmpty()) {
            body.put("label", note);
        }
        WalletActionView view = companion.createAction(body);

        String phone = ctx.phone();
        String link = companionProps.publicUrl().replaceAll("/+$", "") + "/a/" + view.id();
        String message = "*Ready to confirm*\n\n" + view.summary()
                + (view.needsContact() ? "\n\nThe app will ask you to pick the contact on your phone." : "")
                + "\n\nOpen this link on your phone to review it and approve:\n" + link
                + "\n\nYou approve with your fingerprint, face or phone lock, then finish inside M-Pesa by entering your PIN there."
                + " The link works for about 30 minutes.";
        ctx.defer(s -> {
            s.text(phone, message);
            s.buttons(phone, "Changed your mind?", null, List.of(new Button("WCAN:" + view.id(), "Cancel request")));
        });
        return Result.ok("Request prepared: " + view.summary() + " (reference " + shortId(view.id()) + "). "
                + "A message with the approval link is being sent to the customer. Tell them in one short sentence to open the link "
                + "and approve in the app. Do not say that any money was sent or that the payment is done."
                + ("TEXT".equals(source) ? "" : " The details came from " + source.toLowerCase(Locale.ENGLISH)
                + ", so remind them to check every number in the app."));
    }

    static String riskier(String a, String b) {
        int ia = Math.max(0, RISK.indexOf(a == null ? "" : a.toUpperCase(Locale.ENGLISH)));
        int ib = Math.max(0, RISK.indexOf(b == null ? "" : b.toUpperCase(Locale.ENGLISH)));
        return RISK.get(Math.max(ia, ib));
    }

    /** The same message asking for the same thing twice (a retried model call) gives the same request. */
    static String idempotencyKey(String messageId, String type, int amount, Map<String, String> details) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String canonical = messageId + "|" + type + "|" + amount + "|" + new java.util.TreeMap<>(details);
            return "wa-" + HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8))).substring(0, 40);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    // ------------------------------------------------------------ read and cancel

    private Result list(ToolContext ctx, JsonNode input) {
        int limit = Math.max(1, Math.min(input.path("limit").asInt(5), 10));
        List<WalletActionView> actions = companion.list(ctx.phone(), limit);
        if (actions.isEmpty()) {
            return Result.ok("The customer has no requests yet.");
        }
        StringBuilder sb = new StringBuilder("Requests, newest first (status as reported, the app cannot verify with Safaricom):\n");
        for (WalletActionView a : actions) {
            sb.append("- ").append(shortId(a.id())).append(" | ").append(a.status()).append(" | ").append(a.summary());
            if (a.receipt() != null) {
                sb.append(" | M-Pesa code ").append(a.receipt());
            }
            if (a.verification() != null && a.verification().startsWith("SMS_")) {
                sb.append(" | matched with the M-Pesa message on the customer's phone")
                        .append("SMS_AMOUNT".equals(a.verification()) ? " (amount only, recipient not shown)" : " (including the recipient)");
            }
            sb.append(" | id ").append(a.id()).append('\n');
        }
        return Result.ok(sb.toString().strip());
    }

    private Result cancel(ToolContext ctx, JsonNode input) {
        String raw = ToolExecutor.text(input, "request_id", 60);
        UUID id;
        if (raw.isEmpty()) {
            Optional<WalletActionView> waiting = companion.list(ctx.phone(), 10).stream()
                    .filter(a -> "CREATED".equals(a.status()) || "APPROVED".equals(a.status())).findFirst();
            if (waiting.isEmpty()) {
                return Result.ok("There is no waiting request to cancel.");
            }
            id = waiting.get().id();
        } else {
            id = parse(raw);
        }
        WalletActionView cancelled = companion.cancel(id, ctx.phone());
        return Result.ok("Cancelled: " + cancelled.summary() + ". Nothing was sent.");
    }

    // ------------------------------------------------------------ confirmation reported by the customer

    private Result recordConfirmation(ToolContext ctx, JsonNode input) {
        String receipt = ToolExecutor.text(input, "receipt", 30).toUpperCase(Locale.ENGLISH);
        if (!RECEIPT.matcher(receipt).matches()) {
            throw new ToolException("That does not look like an M-Pesa confirmation code. It has 10 letters and digits, for example SGL1234567.");
        }
        String raw = ToolExecutor.text(input, "request_id", 60);
        WalletActionView target;
        if (!raw.isEmpty()) {
            target = companion.get(parse(raw), ctx.phone());
        } else {
            Integer amount = input.hasNonNull("amount") ? ToolExecutor.positiveInt(input, "amount") : null;
            target = companion.list(ctx.phone(), 10).stream()
                    .filter(a -> "HANDED_OFF".equals(a.status()) || "APPROVED".equals(a.status())
                            || ("DONE_REPORTED".equals(a.status()) && a.receipt() == null))
                    .filter(a -> amount == null || a.amount() == amount)
                    .findFirst().orElse(null);
            if (target == null) {
                return Result.ok("No request that was taken to M-Pesa matches. Nothing was recorded. "
                        + "Ask the customer which request it was, or say you have noted the code but cannot link it.");
            }
        }
        WalletActionView updated = companion.attachReceipt(target.id(), ctx.phone(), receipt);
        return Result.ok("Recorded M-Pesa code " + receipt + " against: " + updated.summary() + ". "
                + "This is what the customer reported. It has not been checked with Safaricom, so do not call it confirmed or verified.");
    }

    // ------------------------------------------------------------ contacts

    /**
     * The contacts stay on the customer's side (their phone, Google, Microsoft or a file). This only asks the
     * app to open its finder; what the customer picks comes back as a shared contact.
     */
    private Result findContact(ToolContext ctx, JsonNode input) {
        String name = input.path("name").asString("").strip();
        if (name.isEmpty()) {
            throw new ToolException("Please include the name to look for.");
        }
        String query = name.length() > 80 ? name.substring(0, 80) : name;
        String phone = ctx.phone();
        ctx.defer(s -> s.contactLookup(phone, query));
        return Result.ok("The contact finder is being shown to the customer for \"" + query + "\". Tell them in one short sentence to tap the right person. "
                + "You will not see their contacts. Their choice arrives as a shared contact, and only then can you prepare the request.");
    }

    // ------------------------------------------------------------ helpers

    private static UUID parse(String value) {
        try {
            return UUID.fromString(value.strip());
        } catch (IllegalArgumentException e) {
            throw new ToolException("That is not a valid request id. Use list_wallet_requests to find it.");
        }
    }

    private static String shortId(UUID id) {
        return id == null ? "" : id.toString().substring(0, 8);
    }

    /** Used by messages that show an amount from a view. */
    static String kes(int amount) {
        return Money.kes(amount);
    }
}