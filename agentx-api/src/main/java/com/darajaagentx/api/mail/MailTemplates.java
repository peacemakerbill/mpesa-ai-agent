package com.darajaagentx.api.mail;

import com.darajaagentx.api.mail.MailRenderer.Tone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The automatic emails: what each one says and which data goes into it. The look is not here, it is in the files under
 * {@code src/main/resources/mail/templates} (see {@link MailRenderer}). Everything that came from a person (names,
 * summaries, titles) is escaped for HTML by the template engine, and the subject never carries a line break.
 *
 * Emails are sent as HTML only. Each message also carries a short plain digest of the same facts, which is not sent: it is
 * what the log transport prints (so a developer can read a verification code without a mail server) and what tests read.
 * It is also the whole email if an edited template file is broken.
 * Each email has a variant that takes the time it is sent. The shorter one uses the clock, the longer one is for the
 * callers that already have a clock, and for tests.
 */
public final class MailTemplates {

    private static final Logger log = LoggerFactory.getLogger(MailTemplates.class);
    private static final ZoneId NAIROBI = MailRenderer.NAIROBI;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH).withZone(NAIROBI);

    /** True in tests, so a template that asks for data nobody gave fails loudly instead of showing an empty space. */
    private static volatile boolean strict = false;

    /** One email: the subject, a short plain digest (for the log and for tests, never sent) and the HTML that is sent. */
    public record Message(String subject, String text, String html) {
    }

    private MailTemplates() {
    }

    static void strict(boolean on) {
        strict = on;
    }

    public static String when(Instant at) {
        return WHEN.format(at) + " (Nairobi time)";
    }

    // ------------------------------------------------------------------ the emails

    public static Message welcome(String brand, String name) {
        return welcome(brand, name, Instant.now());
    }

    public static Message welcome(String brand, String name, Instant at) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("greeting", MailRenderer.greeting(at, name));
        d.put("features", List.of(
                item("title", "Pay by chat", "text", "Tell the chat what you want to pay and it prepares the request for you."),
                item("title", "Business accounts", "text", "Set up your business accounts once, then send requests against them from the chat."),
                item("title", "Reports", "text", "Ask for a report of your payments or requests and have it emailed as a PDF, Excel or CSV file.")));
        String digest = MailRenderer.greeting(at, name) + "\n\nYour " + brand + " account is ready, and this email address is now verified, so reports and alerts can reach you here.";
        return build("welcome", brand, at, Tone.OK, "Welcome to " + brand, "Welcome to " + brand, "Email verified",
                "Your account is ready and this address will receive your reports and alerts.", null, digest, d);
    }

    public static Message signIn(String brand, String name, String device, Instant at) {
        String where = device == null || device.isBlank() ? "a device" : device.trim();
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("greeting", MailRenderer.greeting(at, name));
        d.put("device", where);
        d.put("when", when(at));
        d.put("rows", List.of(row("Device", where, false), row("Signed in", when(at), false)));
        callout(d, "Was this not you?", "If this was you, there is nothing to do. If it was not, open the app, go to Settings and sign out the other device, then contact support.");
        return build("sign-in", brand, at, Tone.NOTICE, "New sign in to your " + brand + " account", "New sign in to your account", "Security notice",
                "Your account was signed in on " + where + ".", null,
                MailRenderer.greeting(at, name) + "\n\nYour " + brand + " account was signed in on " + where + " at " + when(at) + ".", d);
    }

    public static Message payment(String brand, String name, boolean ok, String title, String summary, String result, String receipt) {
        return payment(brand, name, ok, title, summary, result, receipt, Instant.now());
    }

    public static Message payment(String brand, String name, boolean ok, String title, String summary, String result, String receipt, Instant at) {
        String head = title + (ok ? " done" : " failed");
        List<Map<String, Object>> rows = new ArrayList<>();
        if (present(result)) {
            rows.add(row(ok ? "Result" : "Reason", result.trim(), false));
        }
        if (present(receipt)) {
            rows.add(row("M-Pesa code", receipt.trim(), true));
        }
        rows.add(row("Time", when(at), false));
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("greeting", MailRenderer.greeting(at, name));
        d.put("summary", summary == null ? "" : summary.trim());
        d.put("result", result == null ? "" : result.trim());
        d.put("receipt", receipt == null ? "" : receipt.trim());
        d.put("when", when(at));
        d.put("rows", rows);
        d.put("next", !ok);
        callout(d, ok ? "" : "What to do next", ok ? "" : "Open the app, check the request and try again. If it keeps failing, contact support.");
        return build("payment", brand, at, ok ? Tone.OK : Tone.BAD, head, head, ok ? "Completed" : "Failed",
                present(summary) ? summary.trim() : head, null, paymentDigest(MailRenderer.greeting(at, name), head, summary, result, receipt, at), d);
    }

    public static Message emailCode(String brand, String name, String code, int minutes) {
        return emailCode(brand, name, code, minutes, Instant.now());
    }

    public static Message emailCode(String brand, String name, String code, int minutes, Instant at) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("greeting", MailRenderer.greeting(at, name));
        d.put("code", code == null ? "" : code);
        d.put("minutes", String.valueOf(minutes));
        String expires = clock(at.plusSeconds(minutes * 60L));
        d.put("expires", expires);
        List<Map<String, Object>> digits = new ArrayList<>();
        for (char c : d.get("code").toString().toCharArray()) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("d", String.valueOf(c));
            digits.add(one);
        }
        d.put("digits", digits);
        callout(d, "Did not ask for this?", "Ignore this email. Nobody can use the code without your phone.");
        return build("email-code", brand, at, Tone.INFO, "Your " + brand + " email verification code", "Verify your email address", "Verification code",
                "The code works once and expires in " + minutes + " minutes.", null,
                MailRenderer.greeting(at, name) + "\n\nYour " + brand + " email verification code is " + d.get("code") + ". It works once and expires in " + minutes
                        + " minutes, at " + expires + " Nairobi time.", d);
    }

    public static Message report(String brand, String sender, String title, String note, String fileName, int rows, Instant at) {
        String who = sender == null || sender.isBlank() ? "A " + brand + " customer" : sender.trim();
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("who", who);
        d.put("title", title == null ? "" : title);
        d.put("note", note == null ? "" : note.trim());
        d.put("fileName", fileName == null ? "" : fileName);
        d.put("ext", extension(fileName));
        d.put("rowsLabel", rows + (rows == 1 ? " row" : " rows"));
        d.put("when", when(at));
        String reason = who + " sent you this report from " + brand + ". If you were not expecting it, you can ignore this email.";
        return build("report", brand, at, Tone.INFO, title + " (" + fileName + ")", title, "Report attached",
                who + " sent you " + fileName + ".", reason,
                "Hello,\n\n" + who + " sent you a report: " + title + ".\n" + (present(note) ? "\nMessage: " + note.trim() + "\n" : "")
                        + "\nAttached: " + fileName + " (" + d.get("rowsLabel") + "), made " + when(at) + ".", d);
    }

    /** Tells the owner a report went to other people. */
    public static Message reportNotice(String brand, String name, String title, List<String> recipients, String fileName) {
        return reportNotice(brand, name, title, recipients, fileName, Instant.now());
    }

    public static Message reportNotice(String brand, String name, String title, List<String> recipients, String fileName, Instant at) {
        String list = String.join(", ", recipients);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("greeting", MailRenderer.greeting(at, name));
        d.put("title", title == null ? "" : title);
        d.put("fileName", fileName == null ? "" : fileName);
        d.put("recipients", list);
        d.put("rows", List.of(row("Report", title == null ? "" : title, false), row("File", fileName == null ? "" : fileName, false),
                row("Sent to", list, false), row("Sent", when(at), false)));
        callout(d, "Did not ask for this?", "If you did not ask for this, sign out your other devices in the app and contact support.");
        return build("report-notice", brand, at, Tone.NOTICE, "A report was emailed from your " + brand + " account", "A report was emailed from your account",
                "Security notice", "A report was sent to " + list + ".", null,
                MailRenderer.greeting(at, name) + "\n\nA report called \"" + title + "\" (" + fileName + ") was emailed from your " + brand + " account to: " + list + ".", d);
    }

    // ------------------------------------------------------------------ building blocks

    private static Message build(String template, String brand, Instant at, Tone tone, String subject, String heading, String status,
                                 String preheader, String reason, String digest, Map<String, Object> data) {
        try {
            String html = MailRenderer.render(template, brand, at, tone, subject, heading, status, preheader, reason, data, strict);
            return new Message(subject, digest, html);
        } catch (RuntimeException e) {
            if (strict) {
                throw e;
            }
            // A broken edited template must never stop an email: send the facts as plain text.
            log.warn("The email template \"{}\" could not be used, sending plain text instead: {}", template, e.getMessage());
            return new Message(subject, digest, null);
        }
    }

    private static String paymentDigest(String greeting, String head, String summary, String result, String receipt, Instant at) {
        StringBuilder sb = new StringBuilder(greeting).append("\n\n").append(head);
        if (present(summary)) {
            sb.append('\n').append(summary.trim());
        }
        if (present(result)) {
            sb.append("\n\n").append(result.trim());
        }
        if (present(receipt)) {
            sb.append("\n\nM-Pesa code: ").append(receipt.trim());
        }
        return sb.append("\n\nTime: ").append(when(at)).toString();
    }

    private static void callout(Map<String, Object> d, String title, String text) {
        d.put("calloutTitle", title);
        d.put("calloutText", text);
    }

    private static Map<String, Object> item(String k1, String v1, String k2, String v2) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    private static Map<String, Object> row(String label, String value, boolean mono) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("value", value);
        m.put("mono", mono);
        return m;
    }

    private static boolean present(String s) {
        return s != null && !s.isBlank();
    }

    private static String clock(Instant at) {
        return DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH).withZone(NAIROBI).format(at);
    }

    /** "PDF" from "report.pdf", and "FILE" when the name has no usable ending. */
    static String extension(String fileName) {
        if (fileName == null) {
            return "FILE";
        }
        int dot = fileName.lastIndexOf('.');
        String ext = dot < 0 ? "" : fileName.substring(dot + 1).replaceAll("[^A-Za-z0-9]", "");
        return ext.isEmpty() || ext.length() > 4 ? "FILE" : ext.toUpperCase(Locale.ROOT);
    }

    /** Escapes text for HTML. */
    public static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}