package com.darajaagentx.api.mail;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** A test copy marks every email, so nobody takes it for a real one. */
class TaggedMailTransportTest {

    private static final class Recorder implements MailTransport {
        final List<OutgoingMail> sent = new ArrayList<>();

        @Override
        public void send(OutgoingMail mail) {
            sent.add(mail);
        }

        @Override
        public String describe() {
            return "recorder";
        }

        @Override
        public boolean delivers() {
            return true;
        }
    }

    @Test
    void theSubjectGetsTheTagAndEverythingElseStays() {
        Recorder inner = new Recorder();
        MailTransport transport = new TaggedMailTransport(inner, "[STAGING] ");
        transport.send(new OutgoingMail(List.of("a@example.com"), "Your code", "text", "<p>html</p>", List.of(), "me@example.com"));

        OutgoingMail got = inner.sent.get(0);
        assertThat(got.subject()).isEqualTo("[STAGING] Your code");
        assertThat(got.to()).containsExactly("a@example.com");
        assertThat(got.text()).isEqualTo("text");
        assertThat(got.html()).isEqualTo("<p>html</p>");
        assertThat(got.replyTo()).isEqualTo("me@example.com");
        assertThat(transport.describe()).contains("recorder").contains("[STAGING]");
        assertThat(transport.delivers()).isTrue();
    }

    @Test
    void aBlankTagChangesNothing() {
        Recorder inner = new Recorder();
        new TaggedMailTransport(inner, null).send(OutgoingMail.of(List.of("a@example.com"), "Hi", "text", null));
        assertThat(inner.sent.get(0).subject()).isEqualTo("Hi");
    }
}
