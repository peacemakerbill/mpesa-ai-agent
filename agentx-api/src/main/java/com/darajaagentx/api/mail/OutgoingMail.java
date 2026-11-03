package com.darajaagentx.api.mail;

import java.util.List;

/**
 * One email. [html] may be null for a plain text email. Every address was already checked with Emails.clean,
 * which refuses line breaks, so none of these can add a header.
 */
public record OutgoingMail(List<String> to, String subject, String text, String html, List<Attachment> attachments) {

    public record Attachment(String fileName, String contentType, byte[] data) {
    }

    public OutgoingMail {
        to = to == null ? List.of() : List.copyOf(to);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        subject = subject == null ? "" : subject.replaceAll("[\\r\\n]+", " ").trim();
    }

    public static OutgoingMail of(List<String> to, String subject, String text, String html) {
        return new OutgoingMail(to, subject, text, html, List.of());
    }
}
