package com.darajaagentx.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Everything about email. The mail server settings are under {@code smtp}. With transport {@code log} nothing leaves
 * this service: each email is written to the log, which is what a developer wants until a mail server is ready.
 *
 * @param transport         smtp (send through the server below) or log (write to the log only)
 * @param from              the sender address, for example no-reply@yourcompany.co.ke. Must be allowed by the SMTP server.
 * @param fromName          the sender name people see
 * @param replyTo           where replies go when an email has no reply address of its own, blank for none. A report a
 *                          customer sends to other people already carries that customer's verified profile email
 * @param smtp              the mail server
 * @param notifications    which automatic emails exist at all. A customer can still turn some off for themselves.
 * @param reports           limits for reports a customer asks to have emailed from the chat or the app
 * @param codeTtl           how long an emailed verification code works
 * @param codeMaxAttempts   wrong tries before the code is dropped
 * @param codesPerHour      emailed codes one customer may ask for per hour
 * @param signInAlertGap    the least time between two "new sign in" emails to the same customer
 */
@ConfigurationProperties(prefix = "agentx.mail")
public record MailProperties(
        @DefaultValue("log") String transport,
        @DefaultValue("no-reply@localhost") String from,
        @DefaultValue("Daraja Agent-X") String fromName,
        @DefaultValue("") String replyTo,
        @DefaultValue Smtp smtp,
        @DefaultValue Notifications notifications,
        @DefaultValue Reports reports,
        @DefaultValue("PT10M") Duration codeTtl,
        @DefaultValue("5") int codeMaxAttempts,
        @DefaultValue("5") int codesPerHour,
        @DefaultValue("PT30M") Duration signInAlertGap
) {

    /**
     * @param startTls true for port 587, false with ssl true for port 465
     * @param password the mail server password. It is read forgivingly: quotes around it are dropped, and for Gmail (or a
     *                 password written the way Google shows an app password, four groups of four letters) the spaces
     *                 are dropped too, so it can be pasted as it was given
     */
    public record Smtp(
            @DefaultValue("") String host,
            @DefaultValue("587") int port,
            @DefaultValue("") String username,
            @DefaultValue("") String password,
            @DefaultValue("true") boolean startTls,
            @DefaultValue("false") boolean ssl,
            @DefaultValue("PT10S") Duration connectTimeout,
            @DefaultValue("PT30S") Duration timeout
    ) {
        public Smtp {
            username = unquote(username);
            password = cleanPassword(host, password);
        }

        private static String unquote(String value) {
            if (value == null) {
                return "";
            }
            String v = value.strip();
            while (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'")))) {
                v = v.substring(1, v.length() - 1).strip();
            }
            return v;
        }

        private static String cleanPassword(String host, String value) {
            String v = unquote(value);
            String h = host == null ? "" : host.toLowerCase(Locale.ROOT);
            boolean google = h.endsWith("gmail.com") || h.endsWith("googlemail.com");
            if (google || v.matches("[A-Za-z]{4}( [A-Za-z]{4}){3}")) {
                v = v.replaceAll("[\\s\\u00A0\\u200B\\u202F]+", "");
            }
            return v;
        }
    }

    public record Notifications(
            @DefaultValue("true") boolean welcome,
            @DefaultValue("true") boolean signIn,
            @DefaultValue("true") boolean payments
    ) {
    }

    /**
     * @param maxRecipients  addresses on one report
     * @param perHour        reports one customer may send per hour
     * @param maxRows        rows in one report
     * @param maxAttachmentBytes the biggest attachment, most mail servers refuse more than 10 to 25 MB
     * @param allowExternal  false means a report can only go to the customer's own verified email and their saved recipients
     * @param allowedDomains when not empty, reports can only go to these domains (the customer's own verified email always works)
     */
    public record Reports(
            @DefaultValue("10") int maxRecipients,
            @DefaultValue("10") int perHour,
            @DefaultValue("5000") int maxRows,
            @DefaultValue("8388608") int maxAttachmentBytes,
            @DefaultValue("true") boolean allowExternal,
            @DefaultValue List<String> allowedDomains
    ) {
        public Reports {
            allowedDomains = allowedDomains == null ? List.of() : List.copyOf(allowedDomains);
        }
    }
}