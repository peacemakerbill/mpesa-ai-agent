package com.darajaagentx.api.mail;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutgoingMailTest {

    @Test
    void thereIsNoReplyAddressUnlessOneIsGiven() {
        assertThat(OutgoingMail.of(List.of("a@example.com"), "Hi", "text", null).replyTo()).isNull();
        assertThat(OutgoingMail.of(List.of("a@example.com"), "Hi", "text", null).withReplyTo("  ").replyTo()).isNull();
    }

    @Test
    void aReplyAddressIsKeptAndTidied() {
        OutgoingMail m = OutgoingMail.of(List.of("a@example.com"), "Hi", "text", null).withReplyTo(" me@example.com ");
        assertThat(m.replyTo()).isEqualTo("me@example.com");
        assertThat(m.to()).containsExactly("a@example.com");
    }

    @Test
    void aReplyAddressCannotAddAHeader() {
        OutgoingMail m = OutgoingMail.of(List.of("a@example.com"), "Hi", "text", null);
        assertThatThrownBy(() -> m.withReplyTo("me@example.com\r\nBcc: x@y.com")).isInstanceOf(IllegalArgumentException.class);
    }
}