package com.darajaagentx.api.mail;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MailTemplatesTest {

    static final Instant AT = Instant.parse("2026-10-29T08:30:00Z");

    @Test
    void everythingAPersonTypedIsEscapedInTheHtmlPart() {
        String evil = "<img src=x onerror=alert(1)>\"'&";
        MailTemplates.Message m = MailTemplates.payment("Brand", evil, true, evil, evil, evil, evil);
        assertThat(m.html()).doesNotContain("<img");
        assertThat(m.html()).contains("&lt;img src=x onerror=alert(1)&gt;&quot;&#39;&amp;");
        MailTemplates.Message r = MailTemplates.report("Brand", evil, evil, evil, "file.pdf", 1, AT);
        assertThat(r.html()).doesNotContain("<img");
        MailTemplates.Message n = MailTemplates.reportNotice("Brand", evil, evil, List.of(evil), "file.pdf");
        assertThat(n.html()).doesNotContain("<img");
        MailTemplates.Message s = MailTemplates.signIn("Brand", evil, evil, AT);
        assertThat(s.html()).doesNotContain("<img");
        MailTemplates.Message w = MailTemplates.welcome("Brand", evil);
        assertThat(w.html()).doesNotContain("<img");
        MailTemplates.Message c = MailTemplates.emailCode("Brand", evil, "123456", 10);
        assertThat(c.html()).doesNotContain("<img");
    }

    @Test
    void theSubjectNeverCarriesALineBreakOrACode() {
        MailTemplates.Message c = MailTemplates.emailCode("Brand", "Amina", "123456", 10);
        assertThat(c.subject()).doesNotContain("123456");
        assertThat(c.text()).contains("code is 123456");
        assertThat(c.text()).contains("10 minutes");
        OutgoingMail mail = OutgoingMail.of(List.of("a@b.co"), "Line one\r\nBcc: x@y.co", "t", null);
        assertThat(mail.subject()).doesNotContain("\n");
        assertThat(mail.subject()).doesNotContain("\r");
    }

    @Test
    void timesAreShownInNairobi() {
        assertThat(MailTemplates.when(AT)).isEqualTo("29 Oct 2026, 11:30 (Nairobi time)");
        assertThat(MailTemplates.signIn("Brand", "Amina", "Pixel 8", AT).text()).contains("11:30");
    }

    @Test
    void aFailedPaymentSaysFailed() {
        assertThat(MailTemplates.payment("Brand", "Amina", false, "STK Push", "KES 10", "Insufficient funds", null).subject()).isEqualTo("STK Push failed");
        assertThat(MailTemplates.payment("Brand", "Amina", true, "STK Push", "KES 10", null, "QK1").text()).contains("M-Pesa code: QK1");
    }
}
