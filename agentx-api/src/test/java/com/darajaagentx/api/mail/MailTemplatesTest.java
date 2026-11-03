package com.darajaagentx.api.mail;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** The files in resources/mail/templates and the data that goes into them. */
class MailTemplateFilesTest {

    static final Instant AT = Instant.parse("2026-10-29T08:30:00Z");
    static final List<String> FILES = List.of("layout.html", "layout.txt", "welcome.html", "welcome.txt", "sign-in.html", "sign-in.txt",
            "payment.html", "payment.txt", "email-code.html", "email-code.txt", "report.html", "report.txt", "report-notice.html", "report-notice.txt");

    @BeforeEach
    void strictAndPlain() {
        MailTemplates.strict(true);
        MailRenderer.install(MailBranding.DEFAULT);
    }

    @AfterEach
    void putBack() {
        MailTemplates.strict(false);
        MailRenderer.install(MailBranding.DEFAULT);
    }

    private static List<MailTemplates.Message> all() {
        List<MailTemplates.Message> out = new ArrayList<>();
        out.add(MailTemplates.welcome("Daraja Agent-X", "Amina", AT));
        out.add(MailTemplates.signIn("Daraja Agent-X", "Amina", "Pixel 8", AT));
        out.add(MailTemplates.payment("Daraja Agent-X", "Amina", true, "STK Push", "KES 1,500 to 0712 345 678", "Accepted", "QK1ABC23", AT));
        out.add(MailTemplates.payment("Daraja Agent-X", "Amina", false, "Transfer", "KES 20,000 to Till 123456", "Insufficient funds", null, AT));
        out.add(MailTemplates.emailCode("Daraja Agent-X", "Amina", "493021", 10, AT));
        out.add(MailTemplates.report("Daraja Agent-X", "Amina Wanjiru", "March payments", "Here is the March list.", "march-payments.xlsx", 42, AT));
        out.add(MailTemplates.reportNotice("Daraja Agent-X", "Amina", "March payments", List.of("a@b.co", "c@d.co"), "march-payments.xlsx", AT));
        return out;
    }

    @Test
    void everyTemplateFileExistsAndHasNoEmojiOrArrows() {
        for (String f : FILES) {
            String src = MailRenderer.sourceOf(f);
            assertThat(src).isNotBlank();
            assertThat(hasPictograph(src)).isFalse();
        }
    }

    @Test
    void everyEmailRendersWithNothingLeftOver() {
        for (MailTemplates.Message m : all()) {
            assertThat(m.html()).doesNotContain("{{", "}}", "null");
            assertThat(m.text()).doesNotContain("{{", "}}", "null");
            assertThat(m.html()).startsWith("<!doctype html>");
            assertThat(m.html()).contains("lang=\"en\"", "prefers-color-scheme: dark", "max-width: 620px", "name=\"viewport\"");
            assertThat(hasPictograph(m.html())).isFalse();
            assertThat(hasPictograph(m.text())).isFalse();
            assertThat(m.text().endsWith("\n")).isTrue();
            assertThat(m.text()).doesNotContain("\n\n\n");
        }
    }

    @Test
    void everyEmailCarriesTheDynamicDetails() {
        Pattern ref = Pattern.compile("DA-261029-[0-9A-Z]{4}");
        for (MailTemplates.Message m : all()) {
            assertThat(ref.matcher(m.html()).find()).isTrue();
            assertThat(ref.matcher(m.text()).find()).isTrue();
            assertThat(m.html()).contains("29 Oct 2026", "11:30", "Nairobi time", "© 2026 Daraja Agent-X. All rights reserved.");
            assertThat(m.text()).contains("Thursday, 29 October 2026", "© 2026 Daraja Agent-X. All rights reserved.");
        }
    }

    @Test
    void theGreetingFollowsTheHourInNairobi() {
        assertThat(MailTemplates.welcome("B", "Amina", Instant.parse("2026-10-29T04:00:00Z")).text()).contains("Good morning, Amina.");
        assertThat(MailTemplates.welcome("B", "Amina", Instant.parse("2026-10-29T10:00:00Z")).text()).contains("Good afternoon, Amina.");
        assertThat(MailTemplates.welcome("B", "Amina", Instant.parse("2026-10-29T17:00:00Z")).text()).contains("Good evening, Amina.");
        assertThat(MailTemplates.welcome("B", " ", AT).text()).contains("Good morning.");
    }

    @Test
    void theCodeEmailSaysWhenTheCodeStopsWorking() {
        MailTemplates.Message m = MailTemplates.emailCode("B", "Amina", "493021", 10, AT);
        assertThat(m.html()).contains("493021", "11:40");
        assertThat(m.text()).contains("code is 493021", "expires in 10 minutes, at 11:40 Nairobi time");
        assertThat(m.subject()).doesNotContain("493021");
    }

    @Test
    void aPaymentShowsItsOutcomeInTheStatusAndTheColour() {
        MailTemplates.Message ok = MailTemplates.payment("B", "A", true, "STK Push", "KES 10", "Accepted", "QK1", AT);
        MailTemplates.Message bad = MailTemplates.payment("B", "A", false, "STK Push", "KES 10", "Insufficient funds", null, AT);
        assertThat(ok.html()).contains("Completed", "#1C7C4D", "QK1", "t-ok");
        assertThat(ok.html()).doesNotContain("What to do next");
        assertThat(bad.html()).contains("Failed", "#B3412F", "t-bad", "What to do next", "Insufficient funds");
        assertThat(bad.html()).doesNotContain("M-Pesa code");
        assertThat(bad.text()).contains("check the request and try again");
    }

    @Test
    void aReportShowsTheFileTypeAndAMessageOnlyWhenThereIsOne() {
        MailTemplates.Message with = MailTemplates.report("B", "Amina", "March", "Please check row 3.", "march.pdf", 1, AT);
        MailTemplates.Message without = MailTemplates.report("B", "Amina", "March", "", "march.pdf", 2, AT);
        assertThat(with.html()).contains(">PDF<", "Please check row 3.", "1 row,");
        assertThat(without.html()).contains("2 rows,");
        assertThat(without.html()).doesNotContain("italic");
        assertThat(without.text()).doesNotContain("Message:");
        assertThat(MailTemplates.extension("data.csv")).isEqualTo("CSV");
        assertThat(MailTemplates.extension("noending")).isEqualTo("FILE");
        assertThat(MailTemplates.extension("x.verylongending")).isEqualTo("FILE");
    }

    @Test
    void companyDetailsAppearInTheFooterWhenTheyAreSet() {
        MailRenderer.install(new MailBranding("help@acme.co.ke", "https://www.acme.co.ke/", "Pay with a message", "Acme Payments Ltd", "Westlands, Nairobi", "AP", ""));
        MailTemplates.Message m = MailTemplates.signIn("Daraja Agent-X", "Amina", "Pixel 8", AT);
        assertThat(m.html()).contains("mailto:help@acme.co.ke", "www.acme.co.ke", "© 2026 Acme Payments Ltd. All rights reserved.",
                "Acme Payments Ltd, Westlands, Nairobi", "Pay with a message", ">AP<", "AP-261029-");
        assertThat(m.text()).contains("help@acme.co.ke", "https://www.acme.co.ke/", "Acme Payments Ltd, Westlands, Nairobi");
        MailTemplates.Message w = MailTemplates.welcome("Daraja Agent-X", "Amina", AT);
        assertThat(w.html()).contains("Open Daraja Agent-X", "href=\"https://www.acme.co.ke/\"");
    }

    @Test
    void withoutCompanyDetailsNothingEmptyIsShown() {
        MailTemplates.Message w = MailTemplates.welcome("Daraja Agent-X", "Amina", AT);
        assertThat(w.html()).doesNotContain("mailto:", "Questions?", "Open Daraja Agent-X", "href=\"\"");
        assertThat(w.text()).doesNotContain("Questions?", "Open Daraja Agent-X:");
        assertThat(w.html()).contains(">DA<");
    }

    @Test
    void aFolderOfEditedFilesWinsAndAMissingFileComesFromTheProgram() throws Exception {
        Path dir = Files.createTempDirectory("mail-templates");
        try {
            Files.writeString(dir.resolve("welcome.txt"), "Custom for {{greeting}}\n", StandardCharsets.UTF_8);
            MailRenderer.install(new MailBranding("", "", "tag", "", "", "", dir.toString()));
            MailTemplates.Message m = MailTemplates.welcome("Daraja Agent-X", "Amina", AT);
            assertThat(m.text()).contains("Custom for Good morning, Amina.");
            assertThat(m.html()).contains("Welcome to Daraja Agent-X");
        } finally {
            Files.deleteIfExists(dir.resolve("welcome.txt"));
            Files.deleteIfExists(dir);
        }
    }

    @Test
    void aBrokenEditedFileNeverStopsTheEmail() throws Exception {
        Path dir = Files.createTempDirectory("mail-templates");
        try {
            Files.writeString(dir.resolve("sign-in.html"), "{{#if oops}}never closed", StandardCharsets.UTF_8);
            MailRenderer.install(new MailBranding("", "", "tag", "", "", "", dir.toString()));
            MailTemplates.strict(false);
            MailTemplates.Message m = MailTemplates.signIn("Daraja Agent-X", "Amina", "Pixel 8", AT);
            assertThat(m.html()).isNull();
            assertThat(m.text()).contains("Pixel 8", "New sign in to your account");
            assertThat(m.subject()).isEqualTo("New sign in to your Daraja Agent-X account");
        } finally {
            Files.deleteIfExists(dir.resolve("sign-in.html"));
            Files.deleteIfExists(dir);
        }
    }

    @Test
    void theInitialsOfAProductName() {
        assertThat(MailRenderer.initials("Daraja Agent-X")).isEqualTo("DA");
        assertThat(MailRenderer.initials("M-Ganji")).isEqualTo("MG");
        assertThat(MailRenderer.initials("")).isEqualTo("M");
        assertThat(MailRenderer.websiteLabel("https://www.acme.co.ke/")).isEqualTo("www.acme.co.ke");
    }

    private static boolean hasPictograph(String s) {
        return s.codePoints().anyMatch(c -> c >= 0x1F000 || (c >= 0x2190 && c <= 0x21FF) || (c >= 0x2600 && c <= 0x27BF)
                || (c >= 0x2B00 && c <= 0x2BFF) || c == 0xFE0F || c == 0x200D);
    }
}