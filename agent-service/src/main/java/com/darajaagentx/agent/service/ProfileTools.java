package com.darajaagentx.agent.service;

import com.darajaagentx.agent.client.ProfileApi;
import com.darajaagentx.agent.client.ProfileApi.ProfileApiException;
import com.darajaagentx.agent.service.ToolExecutor.Result;
import com.darajaagentx.agent.service.ToolExecutor.ToolException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The tools for the customer's own profile, their saved email addresses and emailed reports.
 *
 * <ul>
 *   <li>The phone number is the signed in customer's, never one the model chose.</li>
 *   <li>The assistant never sees an emailed verification code. The customer reads it in their inbox and types it in the chat.</li>
 *   <li>A report is built by agentx-api from its own records (business requests, business accounts, payments), or from a table
 *       the assistant just showed in the chat. Secrets are never part of any report.</li>
 *   <li>"my email" means the verified address on the profile. Addresses are never guessed.</li>
 * </ul>
 */
@Component
public class ProfileTools {

    public static final String GET_MY_PROFILE = "get_my_profile";
    public static final String UPDATE_MY_PROFILE = "update_my_profile";
    public static final String REQUEST_EMAIL_CODE = "request_email_verification";
    public static final String CONFIRM_EMAIL_CODE = "confirm_email_code";
    public static final String LIST_EMAIL_RECIPIENTS = "list_email_recipients";
    public static final String SAVE_EMAIL_RECIPIENTS = "save_email_recipients";
    public static final String REMOVE_EMAIL_RECIPIENT = "remove_email_recipient";
    public static final String SEND_REPORT_EMAIL = "send_report_email";

    private static final Set<String> NAMES = Set.of(GET_MY_PROFILE, UPDATE_MY_PROFILE, REQUEST_EMAIL_CODE, CONFIRM_EMAIL_CODE,
            LIST_EMAIL_RECIPIENTS, SAVE_EMAIL_RECIPIENTS, REMOVE_EMAIL_RECIPIENT, SEND_REPORT_EMAIL);

    public static final List<String> SOURCES = List.of("BUSINESS_REQUESTS", "BUSINESS_ACCOUNTS", "PAYMENTS", "TABLE");
    public static final List<String> FORMATS = List.of("pdf", "csv", "xlsx");

    /** Text fields of the profile, as the model names them and as agentx-api names them. */
    private static final Map<String, String> TEXT_FIELDS = Map.of(
            "first_name", "firstName", "middle_name", "middleName", "last_name", "lastName",
            "address", "address", "city", "city", "county", "county", "country", "country");

    private static final Pattern ONE_ADDRESS = Pattern.compile("^[^\\s@,;<>]+@[^\\s@,;<>]+\\.[^\\s@,;<>]+$");
    private static final Pattern CODE = Pattern.compile("^\\d{4,8}$");

    private static final int MAX_PASTE = 20_000;
    private static final int MAX_ROWS = 2_000;
    private static final int MAX_COLUMNS = 30;
    private static final int MAX_CELL = 300;

    private final ProfileApi api;

    public ProfileTools(ProfileApi api) {
        this.api = api;
    }

    public boolean handles(String name) {
        return NAMES.contains(name);
    }

    public Result execute(ToolContext ctx, String name, JsonNode input) {
        try {
            return switch (name) {
                case GET_MY_PROFILE -> profile(ctx);
                case UPDATE_MY_PROFILE -> update(ctx, input);
                case REQUEST_EMAIL_CODE -> requestCode(ctx, input);
                case CONFIRM_EMAIL_CODE -> confirm(ctx, input);
                case LIST_EMAIL_RECIPIENTS -> listRecipients(ctx);
                case SAVE_EMAIL_RECIPIENTS -> saveRecipients(ctx, input);
                case REMOVE_EMAIL_RECIPIENT -> removeRecipient(ctx, input);
                case SEND_REPORT_EMAIL -> sendReport(ctx, input);
                default -> Result.error("Unknown tool " + name);
            };
        } catch (ToolException e) {
            return Result.error(e.getMessage());
        } catch (ProfileApiException e) {
            if (e.status() == 0 || e.status() >= 500) {
                return Result.error("Your profile and email are not available right now. Please try again in a moment.");
            }
            return Result.error(e.getMessage());
        }
    }

    // ------------------------------------------------------------ profile

    private Result profile(ToolContext ctx) {
        return Result.ok(describe(api.profile(ctx.phone())));
    }

    private Result update(ToolContext ctx, JsonNode input) {
        Map<String, Object> changes = new LinkedHashMap<>();
        for (Map.Entry<String, String> field : TEXT_FIELDS.entrySet()) {
            if (input.has(field.getKey()) && !input.get(field.getKey()).isNull()) {
                changes.put(field.getValue(), ToolExecutor.text(input, field.getKey(), 120));
            }
        }
        if (input.has("latitude") && input.has("longitude") && input.get("latitude").isNumber() && input.get("longitude").isNumber()) {
            changes.put("latitude", input.get("latitude").asDouble());
            changes.put("longitude", input.get("longitude").asDouble());
        } else if (input.has("latitude") || input.has("longitude")) {
            throw new ToolException("Give both latitude and longitude, or neither.");
        }
        if (input.path("clear_location").asBoolean(false)) {
            changes.put("clearLocation", true);
        }
        if (input.has("notify_sign_in") && input.get("notify_sign_in").isBoolean()) {
            changes.put("notifySignIn", input.get("notify_sign_in").asBoolean());
        }
        if (input.has("notify_payments") && input.get("notify_payments").isBoolean()) {
            changes.put("notifyPayments", input.get("notify_payments").asBoolean());
        }
        if (changes.isEmpty()) {
            throw new ToolException("Say what to change: a name, the address, the city, the location or the email alert switches. "
                    + "To change the email address use request_email_verification instead.");
        }
        Map<String, Object> saved = api.updateProfile(ctx.phone(), changes);
        return Result.ok("Saved. " + describe(saved) + "\nThe profile picture can only be changed in the app, under Profile.");
    }

    private Result requestCode(ToolContext ctx, JsonNode input) {
        String email = ToolExecutor.text(input, "email", 254);
        if (!ONE_ADDRESS.matcher(email).matches()) {
            throw new ToolException("That does not look like an email address. Ask the customer to type it again.");
        }
        Map<String, Object> before = api.profile(ctx.phone());
        api.requestEmailCode(ctx.phone(), email);
        String note = Boolean.FALSE.equals(before.get("emailDelivers"))
                ? " This server is not set up to send email yet, so the code will not arrive. Tell the customer to ask the administrator to turn email on."
                : "";
        return Result.ok("A 6 digit code was emailed to " + email + ". Ask the customer to open that inbox and type the code here. "
                + "It expires in about 10 minutes. You cannot see the code. The address is not saved as verified until the code is confirmed." + note);
    }

    private Result confirm(ToolContext ctx, JsonNode input) {
        String code = ToolExecutor.text(input, "code", 12).replaceAll("[\\s-]", "");
        if (!CODE.matcher(code).matches()) {
            throw new ToolException("The code is the 6 digits from the email. Ask the customer to type it again.");
        }
        Map<String, Object> saved = api.verifyEmail(ctx.phone(), code);
        return Result.ok("Email verified: " + str(saved.get("email")) + ". Reports and alerts can now be sent to it.");
    }

    // ------------------------------------------------------------ recipients

    private Result listRecipients(ToolContext ctx) {
        List<Map<String, Object>> list = api.recipients(ctx.phone());
        if (list.isEmpty()) {
            return Result.ok("No email addresses are saved. The customer can give some in the chat, or upload a list in the app under Profile, Email recipients.");
        }
        StringBuilder sb = new StringBuilder("Saved email addresses:\n");
        for (Map<String, Object> r : list) {
            sb.append("- ").append(str(r.get("email")));
            if (!str(r.get("name")).isEmpty()) {
                sb.append(" (").append(str(r.get("name"))).append(')');
            }
            sb.append('\n');
        }
        return Result.ok(sb.toString().strip());
    }

    private Result saveRecipients(ToolContext ctx, JsonNode input) {
        String text = ToolExecutor.text(input, "text", MAX_PASTE);
        if (text.isEmpty()) {
            throw new ToolException("Give the addresses to save, typed by the customer or read from the file they attached.");
        }
        String name = ToolExecutor.text(input, "name", 80);
        if (!name.isEmpty() && ONE_ADDRESS.matcher(text).matches()) {
            Map<String, Object> saved = api.addRecipient(ctx.phone(), text, name);
            return Result.ok("Saved " + str(saved.get("email")) + " as " + name + ".");
        }
        Map<String, Object> result = api.addRecipients(ctx.phone(), text);
        StringBuilder sb = new StringBuilder();
        sb.append(number(result.get("added"))).append(" new address(es) saved");
        if (number(result.get("alreadySaved")) > 0) {
            sb.append(", ").append(number(result.get("alreadySaved"))).append(" were already saved");
        }
        sb.append('.');
        if (number(result.get("invalid")) > 0) {
            sb.append(' ').append(number(result.get("invalid"))).append(" entries were not email addresses");
            List<?> samples = result.get("invalidSamples") instanceof List<?> l ? l : List.of();
            if (!samples.isEmpty()) {
                sb.append(" (for example ").append(String.join(", ", samples.stream().limit(3).map(String::valueOf).toList())).append(')');
            }
            sb.append('.');
        }
        if (Boolean.TRUE.equals(result.get("limitReached"))) {
            sb.append(" The list is full, so some addresses were left out. Tell the customer to remove some first.");
        }
        return Result.ok(sb.toString());
    }

    private Result removeRecipient(ToolContext ctx, JsonNode input) {
        String target = ToolExecutor.text(input, "target", 254);
        if (target.isEmpty()) {
            throw new ToolException("Say which address or name to remove.");
        }
        Map<String, Object> removed = api.removeRecipient(ctx.phone(), target);
        return Result.ok("Removed " + str(removed.get("removed")) + " from the saved addresses.");
    }

    // ------------------------------------------------------------ reports

    private Result sendReport(ToolContext ctx, JsonNode input) {
        String source = ToolExecutor.text(input, "source", 30).toUpperCase(Locale.ENGLISH);
        if (!SOURCES.contains(source)) {
            throw new ToolException("source must be one of " + String.join(", ", SOURCES) + ".");
        }
        String format = ToolExecutor.text(input, "format", 20).toLowerCase(Locale.ENGLISH);
        if (format.isEmpty()) {
            format = "pdf";
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source", source);
        put(body, "title", ToolExecutor.text(input, "title", 120));
        body.put("format", format);

        List<String> to = strings(input.get("to"), 50, 254);
        boolean toMe = input.path("to_me").asBoolean(false);
        boolean toSaved = input.path("to_saved").asBoolean(false);
        if (to.isEmpty() && !toMe && !toSaved) {
            // "send it to my email", or no address given at all, goes to the profile email and never to a guess
            toMe = true;
        }
        if (!to.isEmpty()) {
            body.put("to", to);
        }
        body.put("toMe", toMe);
        body.put("toSaved", toSaved);
        put(body, "message", ToolExecutor.text(input, "message", 500));

        if ("TABLE".equals(source)) {
            List<String> columns = strings(input.get("columns"), MAX_COLUMNS, MAX_CELL);
            List<List<String>> rows = rows(input.get("rows"), columns.size());
            if (columns.isEmpty() || rows.isEmpty()) {
                throw new ToolException("For a TABLE report give the column names and the rows, exactly as shown to the customer.");
            }
            body.put("columns", columns);
            body.put("rows", rows);
            List<String> numeric = strings(input.get("numeric_columns"), MAX_COLUMNS, MAX_CELL);
            if (!numeric.isEmpty()) {
                body.put("numericColumns", numeric);
            }
        } else {
            for (String key : List.of("status", "type", "account", "from", "until")) {
                put(body, key, ToolExecutor.text(input, key, 80));
            }
            if (input.has("limit") && input.get("limit").isNumber()) {
                body.put("limit", Math.max(1, Math.min(input.get("limit").asInt(), 5000)));
            }
        }

        Map<String, Object> result = api.sendReport(ctx.phone(), body);
        List<?> sentTo = result.get("sentTo") instanceof List<?> l ? l : List.of();
        String where = String.join(", ", sentTo.stream().map(String::valueOf).toList());
        String file = str(result.get("fileName"));
        int rows = number(result.get("rows"));
        if (!Boolean.TRUE.equals(result.get("delivered"))) {
            return Result.error("The report " + file + " was built for " + where + " but nothing was emailed, because this server is not set up to send email yet. "
                    + "Tell the customer plainly that it was not sent, and that the administrator must turn email on.");
        }
        return Result.ok("Emailed " + file + " (" + str(result.get("format")).toUpperCase(Locale.ENGLISH) + ", " + rows + (rows == 1 ? " row" : " rows")
                + ") to " + where + ". Tell the customer where it went in one short sentence."
                + (toOthers(sentTo) ? " The account owner also gets a notice that a report went to other people." : ""));
    }

    private static boolean toOthers(List<?> sentTo) {
        return sentTo.size() > 1;
    }

    // ------------------------------------------------------------ helpers

    private static String describe(Map<String, Object> p) {
        StringBuilder sb = new StringBuilder("Profile for ").append(str(p.get("phone"))).append(":\n");
        sb.append("- name: ").append(blank(str(p.get("fullName")), "not set")).append('\n');
        String email = str(p.get("email"));
        sb.append("- email: ");
        if (email.isEmpty()) {
            sb.append("not set");
        } else {
            sb.append(email).append(Boolean.TRUE.equals(p.get("emailVerified")) ? " (verified)" : " (not verified, reports to it are blocked)");
        }
        if (!str(p.get("pendingEmail")).isEmpty()) {
            sb.append("; waiting to verify ").append(str(p.get("pendingEmail")));
        }
        sb.append('\n');
        sb.append("- address: ").append(blank(String.join(", ", nonEmpty(str(p.get("address")), str(p.get("city")), str(p.get("county")), str(p.get("country")))), "not set")).append('\n');
        sb.append("- location: ").append(p.get("latitude") == null ? "not set" : "saved").append('\n');
        sb.append("- picture: ").append(Boolean.TRUE.equals(p.get("hasPicture")) ? "yes" : "none").append('\n');
        sb.append("- email alerts: sign in ").append(Boolean.FALSE.equals(p.get("notifySignIn")) ? "off" : "on")
                .append(", payments ").append(Boolean.FALSE.equals(p.get("notifyPayments")) ? "off" : "on");
        if (Boolean.FALSE.equals(p.get("emailDelivers"))) {
            sb.append("\n- note: this server is not set up to send email yet");
        }
        return sb.toString();
    }

    private static List<String> nonEmpty(String... values) {
        List<String> out = new ArrayList<>();
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                out.add(v);
            }
        }
        return out;
    }

    private static String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void put(Map<String, Object> body, String key, String value) {
        if (value != null && !value.isEmpty()) {
            body.put(key, value);
        }
    }

    /** A list of short texts from the model, each cut to size. */
    private static List<String> strings(JsonNode node, int maxItems, int maxLength) {
        List<String> out = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return out;
        }
        for (JsonNode item : node) {
            String s = cell(item, maxLength);
            if (!s.isEmpty()) {
                out.add(s);
            }
            if (out.size() >= maxItems) {
                break;
            }
        }
        return out;
    }

    /** Rows of cells. Every row is cut or padded to the number of columns so the table is square. */
    private static List<List<String>> rows(JsonNode node, int columns) {
        List<List<String>> out = new ArrayList<>();
        if (node == null || !node.isArray() || columns == 0) {
            return out;
        }
        for (JsonNode row : node) {
            if (!row.isArray()) {
                continue;
            }
            List<String> cells = new ArrayList<>();
            for (int i = 0; i < columns; i++) {
                cells.add(i < row.size() ? cell(row.get(i), MAX_CELL) : "");
            }
            out.add(cells);
            if (out.size() >= MAX_ROWS) {
                break;
            }
        }
        return out;
    }

    private static String cell(JsonNode item, int maxLength) {
        if (item == null || item.isNull()) {
            return "";
        }
        String s = item.isString() ? item.asString().strip()
                : item.isIntegralNumber() ? Long.toString(item.asLong())
                : item.isNumber() ? item.decimalValue().toPlainString()
                : item.isBoolean() ? Boolean.toString(item.asBoolean()) : "";
        return s.length() > maxLength ? s.substring(0, maxLength) : s;
    }

    private static int number(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    // ------------------------------------------------------------ what the model is shown

    /** Tool definitions as plain maps, like {@link BusinessTools#definitions()}. */
    public static List<Map<String, Object>> definitions() {
        Map<String, Object> updateProps = new LinkedHashMap<>();
        updateProps.put("first_name", string("First name"));
        updateProps.put("middle_name", string("Middle name. An empty text removes it."));
        updateProps.put("last_name", string("Last name"));
        updateProps.put("address", string("Street or building address. An empty text removes it."));
        updateProps.put("city", string("Town or city"));
        updateProps.put("county", string("County"));
        updateProps.put("country", string("Country"));
        updateProps.put("latitude", decimal("Latitude, only if the customer shared their location. Send with longitude."));
        updateProps.put("longitude", decimal("Longitude, only if the customer shared their location. Send with latitude."));
        updateProps.put("clear_location", bool("True removes the saved location"));
        updateProps.put("notify_sign_in", bool("Email an alert when the account signs in on a device"));
        updateProps.put("notify_payments", bool("Email a notice when a payment is completed"));

        Map<String, Object> reportProps = new LinkedHashMap<>();
        reportProps.put("source", enumString("What goes in the report. BUSINESS_REQUESTS: the business requests (STK pushes, payouts, balance checks). "
                + "BUSINESS_ACCOUNTS: the saved business accounts, never with keys or passwords. PAYMENTS: the customer's wallet payments. "
                + "TABLE: rows you give, for example the table or statement just shown in this chat.", SOURCES));
        reportProps.put("format", enumString("File type. pdf (default), csv, or xlsx for Excel.", FORMATS));
        reportProps.put("title", string("Title of the report, for example Business requests in November"));
        reportProps.put("to", array("Email addresses to send to, exactly as the customer gave them. May also hold names of saved addresses. Never guess an address."));
        reportProps.put("to_me", bool("Send to the email on the customer's profile. This is the default when no address is given."));
        reportProps.put("to_saved", bool("Send to every saved email address"));
        reportProps.put("message", string("A short note shown in the email. Optional."));
        reportProps.put("status", string("Filter, BUSINESS_REQUESTS and PAYMENTS: for example SUCCEEDED, FAILED, PENDING_APPROVAL. Optional."));
        reportProps.put("type", string("Filter, BUSINESS_REQUESTS: STK_PUSH, B2C, B2B and so on. Optional."));
        reportProps.put("account", string("Filter: one business account by name. Optional."));
        reportProps.put("from", string("Filter: first day, like 2026-11-01. Optional."));
        reportProps.put("until", string("Filter: last day, like 2026-11-30. Optional."));
        reportProps.put("limit", integer("Most rows to include. Optional."));
        reportProps.put("columns", array("TABLE only: the column names"));
        reportProps.put("numeric_columns", array("TABLE only: which of the columns hold numbers, so they are right aligned in the file"));
        reportProps.put("rows", Map.of("type", "array", "description", "TABLE only: the rows. Each row is a list of texts, one per column, in column order.",
                "items", Map.of("type", "array", "items", Map.of("type", "string"))));

        return List.of(
                tool(GET_MY_PROFILE,
                        "Show the customer's own profile: name, email and whether it is verified, address, location, picture and email alert switches.",
                        Map.of(), List.of()),
                tool(UPDATE_MY_PROFILE,
                        "Change the customer's profile: first, middle and last name, address, city, county, country, saved location and the email alert switches. "
                                + "Only send the fields they asked to change. The email address and the phone number are not changed here, and the picture is changed in the app.",
                        updateProps, List.of()),
                tool(REQUEST_EMAIL_CODE,
                        "Start setting or changing the customer's email. A 6 digit code is emailed to that address. You never see the code: ask the customer to read it in their inbox and type it in the chat, "
                                + "then call confirm_email_code.",
                        Map.of("email", string("The email address, exactly as the customer typed it")), List.of("email")),
                tool(CONFIRM_EMAIL_CODE,
                        "Finish verifying the email with the 6 digit code the customer typed from their inbox.",
                        Map.of("code", string("The 6 digit code")), List.of("code")),
                tool(LIST_EMAIL_RECIPIENTS,
                        "Show the email addresses the customer saved to send reports to.",
                        Map.of(), List.of()),
                tool(SAVE_EMAIL_RECIPIENTS,
                        "Save email addresses for sending reports. Put in text whatever the customer typed or the text of the list they attached; every address in it is found and saved. "
                                + "For a single address you may add a name such as Accountant.",
                        Map.of("text", string("Addresses, one or many, in any layout (lines, commas, a pasted CSV)"), "name", string("A name for the address when there is exactly one. Optional.")),
                        List.of("text")),
                tool(REMOVE_EMAIL_RECIPIENT,
                        "Remove a saved email address, by the address or by the name it was saved under.",
                        Map.of("target", string("The address or the name")), List.of("target")),
                tool(SEND_REPORT_EMAIL,
                        "Email a report as a PDF, CSV or Excel file. Use it when the customer asks to send, email or export something. Pick the source that matches what they mean, "
                                + "or TABLE with the rows when they mean the table you just showed. With no address given it goes to the email on their profile (it must be verified). "
                                + "If the customer names addresses, send to exactly those. If they say to send it to people but give none and none are saved, ask them. "
                                + "Never invent an address and never put a secret in a report.",
                        reportProps, List.of("source")));
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

    private static Map<String, Object> decimal(String description) {
        return Map.of("type", "number", "description", description);
    }

    private static Map<String, Object> integer(String description) {
        return Map.of("type", "integer", "description", description);
    }

    private static Map<String, Object> bool(String description) {
        return Map.of("type", "boolean", "description", description);
    }

    private static Map<String, Object> array(String description) {
        return Map.of("type", "array", "description", description, "items", Map.of("type", "string"));
    }

    private static Map<String, Object> enumString(String description, List<String> values) {
        return Map.of("type", "string", "description", description, "enum", values);
    }
}
