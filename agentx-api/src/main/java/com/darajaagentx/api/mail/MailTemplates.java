package com.darajaagentx.api.mail;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * The words of every automatic email, as a plain text part and a simple HTML part. Everything that came from a person
 * (names, summaries, titles) is escaped for HTML, and the subject never carries a line break.
 */
public final class MailTemplates {

    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH).withZone(NAIROBI);

    /** One email's words. */
    public record Message(String subject, String text, String html) {
    }

    private MailTemplates() {
    }

    public static String when(Instant at) {
        return WHEN.format(at) + " (Nairobi time)";
    }

    public static Message welcome(String brand, String name) {
        String hello = greeting(name);
        String text = hello + "\n\nYour " + brand + " account is ready. You can chat to pay, set up business accounts and ask for reports."
                + "\n\nAdd your email address under Profile and verify it, so reports and alerts can reach you."
                + "\n\nIf you did not create this account, reply to this email.";
        String html = page(brand, "Welcome to " + brand, pt(hello) + p("Your account is ready. You can chat to pay, set up business accounts and ask for reports.")
                + p("Add your email address under <b>Profile</b> and verify it, so reports and alerts can reach you.")
                + small("If you did not create this account, reply to this email."));
        return new Message("Welcome to " + brand, text, html);
    }

    public static Message signIn(String brand, String name, String device, Instant at) {
        String where = device == null || device.isBlank() ? "a device" : device.trim();
        String text = greeting(name) + "\n\nYour " + brand + " account was signed in on " + where + " at " + when(at) + "."
                + "\n\nIf this was you, there is nothing to do. If it was not, open the app, go to Settings and sign out the other device, then contact support.";
        String html = page(brand, "New sign in", pt(greeting(name))
                + p("Your account was signed in on <b>" + esc(where) + "</b> at " + esc(when(at)) + ".")
                + p("If this was you, there is nothing to do. If it was not, open the app, go to Settings and sign out the other device, then contact support."));
        return new Message("New sign in to your " + brand + " account", text, html);
    }

    public static Message payment(String brand, String name, boolean ok, String title, String summary, String result, String receipt) {
        String head = title + (ok ? " done" : " failed");
        StringBuilder text = new StringBuilder(greeting(name)).append("\n\n").append(head).append("\n").append(summary == null ? "" : summary);
        if (result != null && !result.isBlank()) {
            text.append("\n\n").append(result);
        }
        if (receipt != null && !receipt.isBlank()) {
            text.append("\n\nM-Pesa code: ").append(receipt);
        }
        StringBuilder html = new StringBuilder(pt(greeting(name))).append("<p style=\"margin:0 0 6px;font-size:16px\"><b>")
                .append(esc(head)).append("</b></p>").append(pt(summary));
        if (result != null && !result.isBlank()) {
            html.append(pt(result));
        }
        if (receipt != null && !receipt.isBlank()) {
            html.append(p("M-Pesa code: <b>" + esc(receipt) + "</b>"));
        }
        return new Message(head, text.toString(), page(brand, head, html.toString()));
    }

    public static Message emailCode(String brand, String name, String code, int minutes) {
        String text = greeting(name) + "\n\nYour " + brand + " email verification code is " + code + ". It works once and expires in " + minutes
                + " minutes.\n\nIf you did not ask for it, ignore this email. Nobody can use the code without your phone.";
        String html = page(brand, "Verify your email", pt(greeting(name)) + p("Your verification code is")
                + "<p style=\"margin:0 0 16px;font-size:32px;letter-spacing:6px;font-weight:700\">" + esc(code) + "</p>"
                + p("It works once and expires in " + minutes + " minutes.")
                + small("If you did not ask for it, ignore this email."));
        return new Message("Your " + brand + " email verification code", text, html);
    }

    public static Message report(String brand, String sender, String title, String note, String fileName, int rows, Instant at) {
        String who = sender == null || sender.isBlank() ? "A " + brand + " customer" : sender;
        String text = "Hello,\n\n" + who + " sent you a report: " + title + ".\n"
                + (note == null || note.isBlank() ? "" : "\nMessage: " + note.trim() + "\n")
                + "\nAttached: " + fileName + " (" + rows + (rows == 1 ? " row" : " rows") + "), made " + when(at) + ".\n\nSent with " + brand + ".";
        String html = page(brand, title, p("Hello,") + p(esc(who) + " sent you a report: <b>" + esc(title) + "</b>.")
                + (note == null || note.isBlank() ? "" : "<p style=\"margin:0 0 12px;padding:10px 12px;background:#f3f4f6;border-radius:6px\">" + esc(note.trim()) + "</p>")
                + p("Attached: <b>" + esc(fileName) + "</b> (" + rows + (rows == 1 ? " row" : " rows") + "), made " + esc(when(at)) + ".")
                + small("Sent with " + esc(brand) + "."));
        return new Message(title + " (" + fileName + ")", text, html);
    }

    /** Tells the owner a report went to other people. */
    public static Message reportNotice(String brand, String name, String title, List<String> recipients, String fileName) {
        String list = String.join(", ", recipients);
        String text = greeting(name) + "\n\nA report called \"" + title + "\" (" + fileName + ") was emailed from your " + brand + " account to: " + list
                + ".\n\nIf you did not ask for this, sign out your other devices in the app and contact support.";
        String html = page(brand, "A report was emailed", pt(greeting(name)) + p("A report called <b>" + esc(title) + "</b> (" + esc(fileName)
                + ") was emailed from your account to: <b>" + esc(list) + "</b>.")
                + small("If you did not ask for this, sign out your other devices in the app and contact support."));
        return new Message("A report was emailed from your " + brand + " account", text, html);
    }

    // ------------------------------------------------------------------ building blocks

    private static String greeting(String name) {
        return name == null || name.isBlank() ? "Hello," : "Hello " + name.trim() + ",";
    }

    /** A paragraph from markup the caller built, with esc() already applied to anything a person typed. */
    private static String p(String markup) {
        return "<p style=\"margin:0 0 12px\">" + (markup == null ? "" : markup) + "</p>";
    }

    /** A paragraph from plain text, which is escaped here. */
    private static String pt(String plain) {
        return p(esc(plain));
    }

    private static String small(String text) {
        return "<p style=\"margin:16px 0 0;color:#6b7280;font-size:13px\">" + text + "</p>";
    }

    private static String page(String brand, String heading, String body) {
        return "<!doctype html><html><body style=\"margin:0;padding:24px;background:#f3f4f6;font-family:Arial,Helvetica,sans-serif;color:#111827\">"
                + "<div style=\"max-width:560px;margin:0 auto;background:#ffffff;border-radius:8px;overflow:hidden\">"
                + "<div style=\"padding:16px 24px;background:#0b6b3a;color:#ffffff;font-size:18px\">" + esc(brand) + "</div>"
                + "<div style=\"padding:24px;font-size:15px;line-height:1.5\"><h1 style=\"margin:0 0 16px;font-size:20px\">" + esc(heading) + "</h1>"
                + body + "</div></div></body></html>";
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
