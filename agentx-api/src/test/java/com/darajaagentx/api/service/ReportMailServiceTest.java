package com.darajaagentx.api.service;

import com.darajaagentx.api.config.MailProperties;
import com.darajaagentx.api.dto.ReportEmailRequest;
import com.darajaagentx.api.dto.ReportEmailResult;
import com.darajaagentx.api.exception.TooManyRequestsException;
import com.darajaagentx.api.mail.OutgoingMail;
import com.darajaagentx.api.report.ReportTables;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Who a report goes to, what it holds, and the limits around it. */
class ReportMailServiceTest {

    static final String OWNER = "254712345678";

    private final Fakes.Rig rig;
    private final RecipientService recipients;
    private final ReportMailService reports;

    ReportMailServiceTest() {
        this(Fakes.mailProps());
    }

    private ReportMailServiceTest(MailProperties props) {
        rig = new Fakes.Rig(props, false);
        recipients = new RecipientService(rig.recipients.repo(), 50);
        reports = new ReportMailService(sources(), rig.service, recipients, rig.outbox, rig.mailer, props, rig.limiter, rig.clock);
    }

    private static ReportMailService with(Fakes.Rig rig, RecipientService recipients, MailProperties props) {
        return new ReportMailService(sources(), rig.service, recipients, rig.outbox, rig.mailer, props, rig.limiter, rig.clock);
    }

    private static ReportSources sources() {
        Instant t = Instant.parse("2026-10-20T09:15:00Z");
        List<ReportTables.OperationRow> ops = List.of(
                new ReportTables.OperationRow(t.plusSeconds(86400 * 8), "B2C", "Pay out to a phone (B2C)", "Main shop", 500, "FAILED", "KES 500 to 0712", null, "Insufficient funds"),
                new ReportTables.OperationRow(t.plusSeconds(86400 * 3), "STK_PUSH", "STK Push", "Main shop", 1200, "SUCCEEDED", "KES 1200 from 0722", "QKA1B2C3D4", "Paid"),
                new ReportTables.OperationRow(t, "STK_PUSH", "STK Push", "Till 1", 300, "SUCCEEDED", "KES 300 from 0733", "QKZ9Y8X7W6", "Paid"));
        return new ReportSources() {
            @Override
            public List<ReportTables.OperationRow> operations(String owner) {
                return ops;
            }

            @Override
            public List<ReportTables.AccountRow> accounts(String owner) {
                return List.of(new ReportTables.AccountRow("Main shop", "sandbox", "buy_goods", "174379", null, null, null, "own", "testapi", "STK Push"));
            }

            @Override
            public List<ReportTables.PaymentRow> payments(String owner) {
                return List.of(new ReportTables.PaymentRow(t, "send money", 100, "DONE_REPORTED", "Send KES 100 to Wanjiku", "QK1"));
            }
        };
    }

    private static ReportEmailRequest request(String source, String format, List<String> to) {
        return new ReportEmailRequest(source, null, format, to, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private OutgoingMail last() {
        return rig.mailbox.sent.get(rig.mailbox.sent.size() - 1);
    }

    @Test
    void sayingMyEmailOrNothingAtAllSendsToTheVerifiedProfileEmail() {
        rig.verified(OWNER, "me@example.com");
        ReportEmailResult r = reports.send(OWNER, request("BUSINESS_REQUESTS", "pdf", List.of("my email")));
        assertThat(r.sentTo()).containsExactly("me@example.com");
        assertThat(r.delivered()).isTrue();
        assertThat(r.format()).isEqualTo("PDF");
        assertThat(r.rows()).isEqualTo(3);
        OutgoingMail m = last();
        assertThat(m.attachments()).hasSize(1);
        assertThat(m.attachments().get(0).fileName()).endsWith(".pdf");
        assertThat(m.attachments().get(0).contentType()).isEqualTo("application/pdf");
        assertThat(new String(m.attachments().get(0).data(), 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");

        rig.mailbox.sent.clear();
        ReportEmailResult bare = reports.send(OWNER, request("BUSINESS_REQUESTS", null, null));
        assertThat(bare.sentTo()).containsExactly("me@example.com");
        assertThat(bare.format()).isEqualTo("PDF");
    }

    @Test
    void anUnverifiedOrMissingProfileEmailIsExplained() {
        assertThatThrownBy(() -> reports.send(OWNER, request("BUSINESS_REQUESTS", "pdf", null))).hasMessageContaining("no email on your profile");
        rig.service.requestEmailCode(OWNER, "pending@example.com");
        assertThatThrownBy(() -> reports.send(OWNER, request("BUSINESS_REQUESTS", "pdf", List.of("me")))).hasMessageContaining("pending@example.com is not verified");
        // naming another address works without a profile email
        ReportEmailResult r = reports.send(OWNER, request("BUSINESS_REQUESTS", "csv", List.of("boss@example.com")));
        assertThat(r.sentTo()).containsExactly("boss@example.com");
    }

    @Test
    void formatWordsAreUnderstood() {
        rig.verified(OWNER, "me@example.com");
        assertThat(reports.send(OWNER, request("ACCOUNTS", "Excel", null)).fileName()).endsWith(".xlsx");
        assertThat(reports.send(OWNER, request("ACCOUNTS", "spreadsheet", null)).format()).isEqualTo("XLSX");
        assertThat(reports.send(OWNER, request("ACCOUNTS", ".csv", null)).fileName()).endsWith(".csv");
        assertThat(reports.send(OWNER, request("ACCOUNTS", "PDF", null)).format()).isEqualTo("PDF");
        assertThatThrownBy(() -> reports.send(OWNER, request("ACCOUNTS", "word", null))).hasMessageContaining("pdf, csv or excel");
    }

    @Test
    void addressesNamesAndTheSavedListAreAllUnderstoodAndDeduplicated() {
        rig.verified(OWNER, "me@example.com");
        recipients.add(OWNER, "tax@firm.co.ke", "Accountant");
        recipients.add(OWNER, "law@firm.co.ke", "Lawyer");
        ReportEmailResult r = reports.send(OWNER, request("PAYMENTS", "csv", List.of("me", "my accountant", "Tax@Firm.co.ke", "x@y.com; z@y.com")));
        assertThat(r.sentTo()).containsExactly("me@example.com", "tax@firm.co.ke", "x@y.com", "z@y.com");

        ReportEmailRequest all = new ReportEmailRequest("PAYMENTS", null, "csv", null, null, true, null, null, null, null, null, null, null, null, null, null);
        assertThat(reports.send(OWNER, all).sentTo()).containsExactly("tax@firm.co.ke", "law@firm.co.ke");
    }

    @Test
    void anUnknownNameOrBrokenAddressIsRefusedWithAClearMessage() {
        rig.verified(OWNER, "me@example.com");
        assertThatThrownBy(() -> reports.send(OWNER, request("PAYMENTS", "pdf", List.of("my auditor")))).hasMessageContaining("my auditor");
        assertThatThrownBy(() -> reports.send(OWNER, request("PAYMENTS", "pdf", List.of("a@@b.com")))).hasMessageContaining("a@@b.com");
        assertThat(rig.mailbox.sent).isEmpty();
    }

    @Test
    void whenAReportGoesToSomeoneElseTheOwnerIsTold() {
        rig.verified(OWNER, "me@example.com");
        reports.send(OWNER, request("ACCOUNTS", "pdf", List.of("boss@example.com")));
        assertThat(rig.mailbox.sent).hasSize(2);
        assertThat(rig.mailbox.sent.get(0).to()).containsExactly("boss@example.com");
        assertThat(rig.mailbox.sent.get(1).to()).containsExactly("me@example.com");
        assertThat(rig.mailbox.sent.get(1).text()).contains("boss@example.com");
        rig.mailbox.sent.clear();
        reports.send(OWNER, request("ACCOUNTS", "pdf", List.of("me")));
        assertThat(rig.mailbox.sent).hasSize(1);
    }

    @Test
    void theServerCanLimitWhoReceivesReports() {
        MailProperties closed = Fakes.mailProps(false, List.of());
        Fakes.Rig r = new Fakes.Rig(closed, false);
        RecipientService rec = new RecipientService(r.recipients.repo(), 50);
        ReportMailService svc = with(r, rec, closed);
        r.verified(OWNER, "me@example.com");
        rec.add(OWNER, "tax@firm.co.ke", "Accountant");
        assertThat(svc.send(OWNER, request("ACCOUNTS", "pdf", List.of("accountant"))).sentTo()).containsExactly("tax@firm.co.ke");
        assertThatThrownBy(() -> svc.send(OWNER, request("ACCOUNTS", "pdf", List.of("stranger@evil.com")))).hasMessageContaining("saved list");

        MailProperties domains = Fakes.mailProps(true, List.of("firm.co.ke"));
        Fakes.Rig d = new Fakes.Rig(domains, false);
        ReportMailService svc2 = with(d, new RecipientService(d.recipients.repo(), 50), domains);
        d.verified(OWNER, "me@gmail.com");
        assertThat(svc2.send(OWNER, request("ACCOUNTS", "pdf", List.of("me", "Tax@FIRM.co.ke"))).sentTo()).containsExactly("me@gmail.com", "tax@firm.co.ke");
        assertThatThrownBy(() -> svc2.send(OWNER, request("ACCOUNTS", "pdf", List.of("x@gmail.com")))).hasMessageContaining("firm.co.ke");
    }

    @Test
    void thereIsALimitOnRecipientsAndOnReportsPerHour() {
        rig.verified(OWNER, "me@example.com");
        List<String> many = new java.util.ArrayList<>();
        for (int i = 0; i < 11; i++) {
            many.add("p" + i + "@x.com");
        }
        assertThatThrownBy(() -> reports.send(OWNER, request("ACCOUNTS", "pdf", many))).hasMessageContaining("10 addresses at most");
        for (int i = 0; i < 10; i++) {
            reports.send(OWNER, request("ACCOUNTS", "csv", null));
        }
        assertThatThrownBy(() -> reports.send(OWNER, request("ACCOUNTS", "csv", null))).isInstanceOf(TooManyRequestsException.class);
        rig.clock.advance(Duration.ofMinutes(61));
        reports.send(OWNER, request("ACCOUNTS", "csv", null));
    }

    @Test
    void businessRequestsCanBeNarrowedByStatusTypeAndDate() {
        rig.verified(OWNER, "me@example.com");
        ReportEmailRequest failed = new ReportEmailRequest("BUSINESS_REQUESTS", null, "csv", null, null, null, "please", "failed", null, null, null, null, null, null, null, null);
        ReportEmailResult r = reports.send(OWNER, failed);
        assertThat(r.rows()).isEqualTo(1);
        assertThat(r.title()).isEqualTo("Business requests, failed");
        String csv = new String(last().attachments().get(0).data(), StandardCharsets.UTF_8);
        assertThat(csv).contains("Insufficient funds");
        assertThat(last().text()).contains("Message: please");

        ReportEmailRequest stk = new ReportEmailRequest("BUSINESS_REQUESTS", null, "csv", null, null, null, null, "done", "stk push", "main", "2026-10-01", "2026-10-31", null, null, null, null);
        ReportEmailResult s = reports.send(OWNER, stk);
        assertThat(s.rows()).isEqualTo(1);
        assertThat(new String(last().attachments().get(0).data(), StandardCharsets.UTF_8)).contains("QKA1B2C3D4");

        ReportEmailRequest none = new ReportEmailRequest("BUSINESS_REQUESTS", "October", "csv", null, null, null, null, null, null, null, "2026-11-01", null, null, null, null, null);
        assertThat(reports.send(OWNER, none).rows()).isEqualTo(0);
        assertThatThrownBy(() -> reports.send(OWNER, new ReportEmailRequest("BUSINESS_REQUESTS", null, "csv", null, null, null, null, "weird", null, null, null, null, null, null, null, null)))
                .hasMessageContaining("not a status");
        assertThatThrownBy(() -> reports.send(OWNER, new ReportEmailRequest("BUSINESS_REQUESTS", null, "csv", null, null, null, null, null, null, null, "31/10/2026", null, null, null, null, null)))
                .hasMessageContaining("not a date");
    }

    @Test
    void aTableTheAssistantGivesBecomesTheReport() {
        rig.verified(OWNER, "me@example.com");
        ReportEmailRequest table = new ReportEmailRequest("TABLE", "Daily takings", "xlsx", null, null, null, null, null, null, null, null, null, null,
                List.of("Day", "Till", "Amount (KES)"), List.of("Amount (KES)"),
                List.of(List.of("Mon", "Till 1", "12,500"), List.of("Tue", "Till 1", "9800")));
        ReportEmailResult r = reports.send(OWNER, table);
        assertThat(r.title()).isEqualTo("Daily takings");
        assertThat(r.rows()).isEqualTo(2);
        assertThat(r.fileName()).startsWith("daily-takings-").endsWith(".xlsx");
        assertThat(new String(last().attachments().get(0).data(), 0, 2, StandardCharsets.ISO_8859_1)).isEqualTo("PK");

        assertThatThrownBy(() -> reports.send(OWNER, new ReportEmailRequest("TABLE", null, "pdf", null, null, null, null, null, null, null, null, null, null, List.of(), null, List.of())))
                .hasMessageContaining("at least one column");
        assertThatThrownBy(() -> reports.send(OWNER, new ReportEmailRequest("INVENTORY", null, "pdf", null, null, null, null, null, null, null, null, null, null, null, null, null)))
                .hasMessageContaining("not one of those");
    }

    @Test
    void theLogShowsWhereAReportWentButKeepsNoAttachment() {
        rig.verified(OWNER, "me@example.com");
        reports.send(OWNER, request("ACCOUNTS", "csv", List.of("boss@example.com")));
        var report = rig.logs.rows.stream().filter(l -> l.getKind().equals("REPORT")).findFirst().orElseThrow();
        assertThat(report.getRecipients()).isEqualTo("boss@example.com");
        assertThat(report.getAttachment()).startsWith("business-accounts-");
        assertThat(report.getStatus()).isEqualTo("SENT");
    }

    @Test
    void aServerThatOnlyLogsSaysSo() {
        rig.verified(OWNER, "me@example.com");
        rig.mailbox.delivers = false;
        ReportEmailResult r = reports.send(OWNER, request("ACCOUNTS", "pdf", null));
        assertThat(r.delivered()).isFalse();
    }
}
