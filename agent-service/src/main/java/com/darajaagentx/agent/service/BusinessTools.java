package com.darajaagentx.agent.service;

import com.darajaagentx.agent.client.BusinessApi;
import com.darajaagentx.agent.client.BusinessApi.BusinessApiException;
import com.darajaagentx.agent.service.ToolExecutor.Result;
import com.darajaagentx.agent.service.ToolExecutor.ToolException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The tools that work on a customer's own business accounts (the ones saved under Settings, Business accounts in the app).
 *
 * <ul>
 *   <li>The assistant never sees a consumer key, a secret, a password or a passkey. It sees names and abilities.</li>
 *   <li>It never moves money. It asks agentx-api to prepare a request. Reads and collections go straight to Safaricom.
 *       Anything that sends money out waits for the owner to approve it in the app with fingerprint or phone lock.</li>
 *   <li>The phone number is the signed in customer's, never one the model chose.</li>
 * </ul>
 */
@Component
public class BusinessTools {

    public static final String LIST_BUSINESS_ACCOUNTS = "list_business_accounts";
    public static final String PREPARE_BUSINESS_REQUEST = "prepare_business_request";
    public static final String LIST_BUSINESS_REQUESTS = "list_business_requests";
    public static final String CANCEL_BUSINESS_REQUEST = "cancel_business_request";

    private static final Set<String> NAMES = Set.of(LIST_BUSINESS_ACCOUNTS, PREPARE_BUSINESS_REQUEST, LIST_BUSINESS_REQUESTS, CANCEL_BUSINESS_REQUEST);

    public static final List<String> TYPES = List.of("STK_PUSH", "STK_QUERY", "DYNAMIC_QR", "PULL", "BALANCE", "STATUS", "B2C", "B2POCHI", "B2B", "REVERSAL");

    /** The details a request can carry. Each is passed on as text and agentx-api checks it. */
    public static final List<String> DETAIL_KEYS = List.of("amount", "phone", "reference", "description", "remarks", "occasion", "command",
            "receiver", "target_type", "account_reference", "receipt", "operation", "checkout_request_id", "start_date", "end_date",
            "offset", "merchant_name");

    private final BusinessApi api;
    private final String publicUrl;

    public BusinessTools(BusinessApi api,
                         @Value("${agentx.public-url:${companion.public-url:http://localhost:8084}}") String publicUrl) {
        this.api = api;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    public boolean handles(String name) {
        return NAMES.contains(name);
    }

    public Result execute(ToolContext ctx, String name, JsonNode input) {
        try {
            return switch (name) {
                case LIST_BUSINESS_ACCOUNTS -> listAccounts(ctx);
                case PREPARE_BUSINESS_REQUEST -> prepare(ctx, input);
                case LIST_BUSINESS_REQUESTS -> listRequests(ctx, input);
                case CANCEL_BUSINESS_REQUEST -> cancel(ctx, input);
                default -> Result.error("Unknown tool " + name);
            };
        } catch (ToolException e) {
            return Result.error(e.getMessage());
        } catch (BusinessApiException e) {
            if (e.status() == 0 || e.status() >= 500) {
                return Result.error("Business accounts are not available right now. Please try again in a moment.");
            }
            return Result.error(e.getMessage());
        }
    }

    // ------------------------------------------------------------ accounts

    private Result listAccounts(ToolContext ctx) {
        List<Map<String, Object>> accounts = api.accounts(ctx.phone());
        if (accounts.isEmpty()) {
            return Result.ok("The customer has no business accounts saved. Tell them to add one in the app under Settings, then Business accounts. "
                    + "They need the shortcode, consumer key and secret from the Daraja portal, and for payouts the initiator name and password.");
        }
        StringBuilder sb = new StringBuilder("Business accounts (names and abilities only, you never see keys or passwords):\n");
        for (Map<String, Object> a : accounts) {
            sb.append("- ").append(str(a.get("label"))).append(" | ").append(lower(a.get("environment")))
                    .append(" | ").append(lower(a.get("shortcodeType")).replace('_', ' ')).append(' ').append(str(a.get("shortcode")));
            if (!str(a.get("tillNumber")).isEmpty()) {
                sb.append(" | till ").append(str(a.get("tillNumber")));
            }
            if (!str(a.get("accountReference")).isEmpty()) {
                sb.append(" | account ").append(str(a.get("accountReference")));
            }
            List<String> ready = new ArrayList<>();
            List<String> missing = new ArrayList<>();
            for (Map<String, Object> c : maps(a.get("capabilities"))) {
                if (Boolean.TRUE.equals(c.get("ready"))) {
                    ready.add(str(c.get("label")));
                } else {
                    missing.add(str(c.get("label")) + " needs " + str(c.get("needs")));
                }
            }
            sb.append("\n    can do: ").append(ready.isEmpty() ? "nothing yet" : String.join(", ", ready));
            if (!missing.isEmpty()) {
                sb.append("\n    cannot yet: ").append(String.join("; ", missing));
            }
            sb.append('\n');
        }
        return Result.ok(sb.toString().strip());
    }

    // ------------------------------------------------------------ prepare

    private Result prepare(ToolContext ctx, JsonNode input) {
        String type = ToolExecutor.text(input, "type", 20).toUpperCase(Locale.ENGLISH);
        if (!TYPES.contains(type)) {
            throw new ToolException("type must be one of " + String.join(", ", TYPES) + ".");
        }
        String account = ToolExecutor.text(input, "account", 80).replaceFirst("^@", "").strip();
        Map<String, String> params = new LinkedHashMap<>();
        for (String key : DETAIL_KEYS) {
            String value = scalar(input.get(key));
            if (!value.isEmpty()) {
                params.put(key, value.length() > 120 ? value.substring(0, 120) : value);
            }
        }
        String source = WalletTools.riskier(ctx.source(), ToolExecutor.text(input, "details_from", 12));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("idempotencyKey", idempotencyKey(ctx.messageId(), type, account, params));
        body.put("phone", ctx.phone());
        body.put("account", account);
        body.put("type", type);
        body.put("params", params);
        body.put("source", source);
        Map<String, Object> view = api.prepare(body);

        String status = str(view.get("status"));
        String summary = str(view.get("summary"));
        String id = str(view.get("id"));
        String phone = ctx.phone();
        String warning = "TEXT".equals(source) ? "" : " The details came from " + source.toLowerCase(Locale.ENGLISH) + ", so remind them to check every number in the app.";
        switch (status) {
            case "PENDING_APPROVAL" -> {
                String message = "*Ready to approve*\n\n" + summary
                        + "\n\nOpen this link on your phone to review it and approve:\n" + publicUrl + "/b/" + id
                        + "\n\nYou approve with your fingerprint, face or phone lock. Money moves only after you approve. The link works for about 30 minutes.";
                ctx.defer(s -> s.text(phone, message));
                return Result.ok("Prepared and waiting for the owner's approval in the app: " + summary + " (reference " + shortId(id) + "). "
                        + "A message with the approval link is being sent. Tell them in one short sentence to open the link and approve. "
                        + "Do not say that any money was sent." + warning);
            }
            case "SUBMITTED" -> {
                return Result.ok("Sent to Safaricom: " + summary + " (reference " + shortId(id) + "). " + str(view.get("resultText")) + " "
                        + "The result arrives in this chat by itself. Tell them that in one short sentence and do not say it succeeded yet." + warning);
            }
            case "SUCCEEDED" -> {
                String qr = str(view.get("qrCode"));
                if (!qr.isEmpty()) {
                    byte[] png = Base64.getDecoder().decode(qr);
                    String caption = "Scan with M-Pesa to pay " + str(view.get("accountLabel"));
                    ctx.defer(s -> s.image(phone, png, "image/png", caption));
                    return Result.ok("A QR code is being sent as a picture. Tell them to open M-Pesa, choose Scan QR and scan it.");
                }
                return Result.ok(summary + "\n" + str(view.get("resultText")));
            }
            case "UNKNOWN" -> {
                return Result.ok("Safaricom did not give a clear answer: " + str(view.get("failure"))
                        + " Do not try again. Tell the customer to check the M-Pesa statement first.");
            }
            default -> {
                String why = str(view.get("failure")).isEmpty() ? str(view.get("resultText")) : str(view.get("failure"));
                return Result.error((why.isEmpty() ? "The request did not go through." : why) + " Nothing was sent.");
            }
        }
    }

    // ------------------------------------------------------------ read and cancel

    private Result listRequests(ToolContext ctx, JsonNode input) {
        String one = ToolExecutor.text(input, "request_id", 60);
        if (!one.isEmpty()) {
            return Result.ok(describe(api.operation(fullId(ctx, one), ctx.phone()), true));
        }
        int limit = Math.max(1, Math.min(input.path("limit").asInt(5), 10));
        List<Map<String, Object>> list = api.operations(ctx.phone(), limit);
        if (list.isEmpty()) {
            return Result.ok("There are no business requests yet.");
        }
        StringBuilder sb = new StringBuilder("Business requests, newest first:\n");
        list.forEach(o -> sb.append(describe(o, false)).append('\n'));
        return Result.ok(sb.toString().strip());
    }

    private Result cancel(ToolContext ctx, JsonNode input) {
        String raw = ToolExecutor.text(input, "request_id", 60);
        String id;
        if (raw.isEmpty()) {
            id = api.operations(ctx.phone(), 10).stream().filter(o -> "PENDING_APPROVAL".equals(str(o.get("status")))).map(o -> str(o.get("id")))
                    .findFirst().orElse("");
            if (id.isEmpty()) {
                return Result.ok("There is no business request waiting for approval to cancel.");
            }
        } else {
            id = fullId(ctx, raw);
        }
        Map<String, Object> cancelled = api.cancel(id, ctx.phone());
        return Result.ok("Cancelled: " + str(cancelled.get("summary")) + ". Nothing was sent to Safaricom.");
    }

    /** The model may quote only the first 8 characters of an id, as shown in earlier answers. */
    private String fullId(ToolContext ctx, String raw) {
        String text = raw.strip().toLowerCase(Locale.ENGLISH);
        if (text.length() >= 32) {
            return text;
        }
        List<String> hits = api.operations(ctx.phone(), 10).stream().map(o -> str(o.get("id"))).filter(i -> i.toLowerCase(Locale.ENGLISH).startsWith(text)).toList();
        if (hits.size() == 1) {
            return hits.get(0);
        }
        throw new ToolException("I could not find a business request with that id. Use list_business_requests to see them.");
    }

    private static String describe(Map<String, Object> o, boolean full) {
        StringBuilder sb = new StringBuilder("- ").append(shortId(str(o.get("id")))).append(" | ").append(str(o.get("status")))
                .append(" | ").append(str(o.get("summary")));
        if (!str(o.get("receipt")).isEmpty()) {
            sb.append(" | M-Pesa code ").append(str(o.get("receipt")));
        }
        if (full) {
            if (!str(o.get("resultText")).isEmpty()) {
                sb.append("\n  ").append(str(o.get("resultText")).replace("\n", "\n  "));
            }
            if (!str(o.get("failure")).isEmpty()) {
                sb.append("\n  ").append(str(o.get("failure")));
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------ helpers

    /** The same message asking for the same thing twice (a retried model call) gives the same request. */
    static String idempotencyKey(String messageId, String type, String account, Map<String, String> params) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String canonical = messageId + "|" + type + "|" + account + "|" + new TreeMap<>(params);
            return "bz-" + HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8))).substring(0, 40);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String scalar(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isString()) {
            return node.asString().strip();
        }
        if (node.isIntegralNumber()) {
            return Long.toString(node.asLong());
        }
        if (node.isNumber()) {
            return node.decimalValue().toPlainString();
        }
        if (node.isBoolean()) {
            return Boolean.toString(node.asBoolean());
        }
        return "";
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String lower(Object o) {
        return str(o).toLowerCase(Locale.ENGLISH);
    }

    private static String shortId(String id) {
        return id.length() < 8 ? id : id.substring(0, 8);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object o) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (o instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?>) {
                    out.add((Map<String, Object>) item);
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------ what the model is shown

    /**
     * Tool definitions as plain maps, so ToolCatalog can turn them into JSON with its own mapper:
     * {@code BusinessTools.definitions().forEach(d -> { JsonNode n = mapper.valueToTree(d); tools.add(n); });}
     */
    public static List<Map<String, Object>> definitions() {
        Map<String, Object> prepareProps = new LinkedHashMap<>();
        prepareProps.put("account", string("Which business account, by its name, till number or shortcode. The customer may write it as @Name. "
                + "Leave out only when they have exactly one."));
        prepareProps.put("type", enumString("What to do. STK_PUSH asks a customer to pay (a PIN prompt on their phone). STK_QUERY checks an earlier STK Push. "
                + "DYNAMIC_QR makes a QR code for an amount. PULL lists transactions in a period. BALANCE checks the account balance. STATUS looks up one transaction by its code. "
                + "B2C pays money to a phone number. B2POCHI pays a Pochi la Biashara. B2B pays a Paybill or Till. REVERSAL reverses a customer payment.", TYPES));
        prepareProps.put("amount", integer("Amount in whole Kenyan shillings"));
        prepareProps.put("phone", string("Kenyan phone number: who to charge for STK_PUSH, who to pay for B2C and B2POCHI"));
        prepareProps.put("reference", string("STK_PUSH and DYNAMIC_QR: reference shown to the payer. Letters and digits, at most 12. Optional."));
        prepareProps.put("description", string("STK_PUSH: what it is for, at most 13 characters. Optional."));
        prepareProps.put("remarks", string("B2C, B2POCHI, B2B, REVERSAL: short note. Optional."));
        prepareProps.put("occasion", string("B2C and B2POCHI: optional note"));
        prepareProps.put("command", string("B2C only: BusinessPayment (default), SalaryPayment or PromotionPayment"));
        prepareProps.put("receiver", string("B2B: the Paybill or Till number being paid, 5 to 7 digits"));
        prepareProps.put("target_type", enumString("B2B: is the receiver a paybill or a till", List.of("paybill", "till")));
        prepareProps.put("account_reference", string("B2B to a Paybill: the account number to pay into"));
        prepareProps.put("receipt", string("STATUS and REVERSAL: the M-Pesa transaction code, for example QGH7ABC123"));
        prepareProps.put("operation", string("STK_QUERY: id of the earlier STK Push request, as shown by list_business_requests"));
        prepareProps.put("checkout_request_id", string("STK_QUERY: Safaricom's checkout id, if the customer has it instead"));
        prepareProps.put("start_date", string("PULL: start of the period, like 2026-11-01 or 2026-11-01 08:30"));
        prepareProps.put("end_date", string("PULL: end of the period"));
        prepareProps.put("offset", string("PULL: how many results to skip, for the next page. Default 0."));
        prepareProps.put("merchant_name", string("DYNAMIC_QR: name shown on the QR code. Optional."));
        prepareProps.put("details_from", enumString("Where the numbers came from if not typed: VOICE, IMAGE, DOCUMENT or CONTACT. Optional.", List.of("TEXT", "VOICE", "IMAGE", "DOCUMENT", "CONTACT")));

        return List.of(
                tool(LIST_BUSINESS_ACCOUNTS,
                        "List the customer's saved business accounts (the shortcodes and tills they added in the app) and what each can do. "
                                + "You see names and abilities only, never keys or passwords. Use it when they ask what they can do, or when you are unsure which account they mean.",
                        Map.of(), List.of()),
                tool(PREPARE_BUSINESS_REQUEST,
                        "Prepare a request on one of the customer's business accounts. You never move money yourself. Reads (balance, status, pull, QR, STK query) "
                                + "and STK Push go to Safaricom at once. Anything that sends money out (B2C, Pochi, B2B, reversal) waits for the owner to approve it in the app with "
                                + "fingerprint or phone lock. Say plainly which of the two happened.",
                        prepareProps, List.of("type")),
                tool(LIST_BUSINESS_REQUESTS,
                        "Show recent business requests and what happened to each. With request_id it shows one in full.",
                        Map.of("limit", integer("How many, 1 to 10. Default 5."), "request_id", string("Id of one request. Optional.")), List.of()),
                tool(CANCEL_BUSINESS_REQUEST,
                        "Cancel a business request that is still waiting for the owner's approval. Without request_id it cancels the newest waiting one.",
                        Map.of("request_id", string("Id of the request. Optional.")), List.of()));
    }

    private static Map<String, Object> tool(String name, String description, Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", name);
        tool.put("description", description);
        tool.put("input_schema", schema);
        return tool;
    }

    private static Map<String, Object> string(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static Map<String, Object> integer(String description) {
        return Map.of("type", "integer", "description", description);
    }

    private static Map<String, Object> enumString(String description, List<String> values) {
        return Map.of("type", "string", "description", description, "enum", values);
    }
}
