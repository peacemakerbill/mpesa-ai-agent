package com.darajaagentx.api.service;

import com.darajaagentx.api.config.MailProperties;
import com.darajaagentx.api.dto.ReportEmailRequest;
import com.darajaagentx.api.dto.ReportEmailResult;
import com.darajaagentx.api.exception.InvalidActionRequestException;
import com.darajaagentx.api.exception.TooManyRequestsException;
import com.darajaagentx.api.mail.MailTemplates;
import com.darajaagentx.api.mail.OutgoingMail;
import com.darajaagentx.api.model.EmailRecipient;
import com.darajaagentx.api.model.UserProfile;
import com.darajaagentx.api.report.RenderedReport;
import com.darajaagentx.api.report.ReportData;
import com.darajaagentx.api.report.ReportFormat;
import com.darajaagentx.api.report.ReportRenderer;
import com.darajaagentx.api.report.ReportTables;
import com.darajaagentx.api.util.Emails;
import com.darajaagentx.api.util.PersonNames;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Makes a report and emails it as a PDF, CSV or Excel file.
 *
 * <p>Who gets it: the addresses typed in the request, names from the customer's saved list ("my accountant"), and the
 * customer's own email when they say "me" or name nobody. The customer's own address must be verified. Other addresses are
 * allowed unless the server limits them ({@code agentx.mail.reports}), and when a report goes to anyone else the owner gets
 * a short notice so a report never leaves unseen.
 */
@Service
public class ReportMailService {

    private static final Duration HOUR = Duration.ofHours(1);
    private static final Set<String> ME = Set.of("me", "myself", "my email", "my mail", "my address", "my email address", "self", "profile", "my profile email", "owner");

    private final ReportSources sources;
    private final ProfileService profiles;
    private final RecipientService recipients;
    private final MailOutbox outbox;
    private final CustomerMailer mailer;
    private final MailProperties props;
    private final SlidingWindowLimiter limiter;
    private final Clock clock;

    public ReportMailService(ReportSources sources, ProfileService profiles, RecipientService recipients, MailOutbox outbox,
                             CustomerMailer mailer, MailProperties props, SlidingWindowLimiter limiter, Clock clock) {
        this.sources = sources;
        this.profiles = profiles;
        this.recipients = recipients;
        this.outbox = outbox;
        this.mailer = mailer;
        this.props = props;
        this.limiter = limiter;
        this.clock = clock;
    }

    public ReportEmailResult send(String owner, ReportEmailRequest r) {
        if (r == null) {
            throw new InvalidActionRequestException("Say what report to send.");
        }
        MailProperties.Reports rules = props.reports();
        ReportFormat format = ReportFormat.parse(r.format());
        UserProfile me = profiles.getOrCreate(owner);
        List<String> to = resolve(owner, me, r, rules);

        if (!limiter.allow("report:" + owner, Math.max(1, rules.perHour()), HOUR)) {
            throw new TooManyRequestsException("You have sent many reports in the last hour. Please try again later.");
        }
        Instant now = clock.instant();
        ReportData data = build(owner, r, now, rules.maxRows());
        RenderedReport file = ReportRenderer.render(data, format);
        if (file.data().length > rules.maxAttachmentBytes()) {
            throw new InvalidActionRequestException("The " + format.extension().toUpperCase(Locale.ROOT) + " file is " + (file.data().length / 1_048_576)
                    + " MB, more than an email can carry. Narrow the report by date, or choose CSV, which is smaller.");
        }
        String note = note(r.message());
        MailTemplates.Message m = MailTemplates.report(mailer.brand(), PersonNames.full(me.getFirstName(), me.getMiddleName(), me.getLastName()),
                data.title(), note, file.fileName(), file.rows(), now);
        OutgoingMail mail = new OutgoingMail(to, m.subject(), m.text(), m.html(),
                List.of(new OutgoingMail.Attachment(file.fileName(), file.contentType(), file.data())));
        outbox.send(owner, "REPORT", mail);

        List<String> others = to.stream().filter(a -> !a.equals(me.getEmail())).toList();
        if (!others.isEmpty() && outbox.delivers()) {
            mailer.reportSentElsewhere(me, data.title(), others, file.fileName());
        }
        return new ReportEmailResult(outbox.delivers(), to, file.fileName(), format.name(), file.rows(), file.data().length, data.title());
    }

    // ------------------------------------------------------------------ who

    List<String> resolve(String owner, UserProfile me, ReportEmailRequest r, MailProperties.Reports rules) {
        Set<String> out = new LinkedHashSet<>();
        List<String> problems = new ArrayList<>();
        boolean wantsMe = Boolean.TRUE.equals(r.toMe());
        List<EmailRecipient> saved = recipients.all(owner);
        if (r.to() != null) {
            for (String raw : r.to()) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                String token = raw.trim();
                if (ME.contains(token.toLowerCase(Locale.ROOT))) {
                    wantsMe = true;
                } else if (token.indexOf('@') >= 0) {
                    Emails.Found found = Emails.findAll(token);
                    out.addAll(found.emails());
                    problems.addAll(found.invalid());
                } else {
                    Optional<EmailRecipient> byName = recipients.find(owner, token);
                    if (byName.isPresent()) {
                        out.add(byName.get().getEmail());
                    } else {
                        problems.add(token);
                    }
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new InvalidActionRequestException("I could not use: " + String.join(", ", problems.subList(0, Math.min(5, problems.size())))
                    + ". Give a full email address (name@example.com), or a name from your saved list.");
        }
        if (Boolean.TRUE.equals(r.toSaved())) {
            saved.forEach(s -> out.add(s.getEmail()));
        }
        if (out.isEmpty()) {
            wantsMe = true;           // "send it to my email" and a bare "send it" both mean the profile email
        }
        if (wantsMe) {
            if (!me.isEmailVerified() || me.getEmail() == null) {
                throw new InvalidActionRequestException(me.getPendingEmail() != null
                        ? "Your email " + me.getPendingEmail() + " is not verified yet. Enter the code that was sent to it (Profile, Email), or tell me an address to send to."
                        : "You have no email on your profile yet. Add and verify one under Profile, or tell me the address to send to.");
            }
            Set<String> ordered = new LinkedHashSet<>();
            ordered.add(me.getEmail());
            ordered.addAll(out);
            out.clear();
            out.addAll(ordered);
        }
        if (out.isEmpty()) {
            throw new InvalidActionRequestException("Tell me which email address to send it to.");
        }
        if (out.size() > rules.maxRecipients()) {
            throw new InvalidActionRequestException("A report can go to " + rules.maxRecipients() + " addresses at most. You named " + out.size() + ".");
        }
        Set<String> savedEmails = new LinkedHashSet<>();
        saved.forEach(s -> savedEmails.add(s.getEmail()));
        for (String a : out) {
            boolean own = a.equals(me.getEmail()) && me.isEmailVerified();
            if (own) {
                continue;
            }
            if (!rules.allowExternal() && !savedEmails.contains(a)) {
                throw new InvalidActionRequestException("Reports can only go to your own email or to addresses on your saved list. Add " + a + " to the list first.");
            }
            if (!rules.allowedDomains().isEmpty() && rules.allowedDomains().stream().noneMatch(d -> d.equalsIgnoreCase(Emails.domain(a)))) {
                throw new InvalidActionRequestException("Reports can only be sent to these domains: " + String.join(", ", rules.allowedDomains()) + ".");
            }
        }
        return new ArrayList<>(out);
    }

    // ------------------------------------------------------------------ what

    ReportData build(String owner, ReportEmailRequest r, Instant now, int maxRows) {
        String source = r.source() == null ? "" : r.source().trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (source.isEmpty()) {
            source = r.columns() != null && !r.columns().isEmpty() ? "TABLE" : "BUSINESS_REQUESTS";
        }
        int limit = r.limit() == null ? maxRows : Math.max(1, Math.min(r.limit(), maxRows));
        ReportTables.Filters filters = new ReportTables.Filters(r.status(), r.type(), r.account(), r.from(), r.until());
        String brand = mailer.brand();
        return switch (source) {
            case "TABLE" -> ReportTables.custom(brand, now, r.title(), r.columns(), r.numericColumns(), r.rows(), maxRows);
            case "BUSINESS_REQUESTS", "REQUESTS", "OPERATIONS", "TRANSACTIONS" ->
                    ReportTables.businessRequests(brand, now, r.title(), sources.operations(owner), filters, limit);
            case "BUSINESS_ACCOUNTS", "ACCOUNTS" -> ReportTables.businessAccounts(brand, now, r.title(), sources.accounts(owner), limit);
            case "PAYMENTS", "APP_PAYMENTS", "ACTIONS" -> ReportTables.payments(brand, now, r.title(), sources.payments(owner), filters, limit);
            default -> throw new InvalidActionRequestException("I can build reports of business requests, business accounts and payments, "
                    + "or send a table you give me. \"" + r.source() + "\" is not one of those.");
        };
    }

    private static String note(String message) {
        if (message == null) {
            return null;
        }
        String s = message.replaceAll("[\\p{Cntrl}&&[^\\n]]", " ").trim();
        return s.isEmpty() ? null : (s.length() > 500 ? s.substring(0, 500) : s);
    }
}
