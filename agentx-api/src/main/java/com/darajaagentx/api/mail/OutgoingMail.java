package com.darajaagentx.api.mail;

import java.util.List;

/**
 * One email. [html] may be null for a plain text email. Every address was already checked with Emails.clean,
 * which refuses line breaks, so none of these can add a header.
 *
 * <p>[replyTo] is where a reply goes. Null means the server's default (agentx.mail.reply-to, usually none). A report a
 * customer sends to other people carries the customer's own verified profile email here.
 */
public record OutgoingMail(List<String> to, String subject, String text, String html, List<Attachment> attachments, String replyTo) {

    public record Attachment(String fileName, String contentType, byte[] data) {
    }

    public OutgoingMail {
        to = to == null ? List.of() : List.copyOf(to);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        subject = subject == null ? "" : subject.replaceAll("[\\r\\n]+", " ").trim();
        replyTo = replyTo == null || replyTo.isBlank() ? null : replyTo.trim();
        if (replyTo != null && (replyTo.indexOf('\r') >= 0 || replyTo.indexOf('\n') >= 0)) {
            throw new IllegalArgumentException("The reply address must be one address on one line.");
        }
    }

    public OutgoingMail(List<String> to, String subject, String text, String html, List<Attachment> attachments) {
        this(to, subject, text, html, attachments, null);
    }

    public static OutgoingMail of(List<String> to, String subject, String text, String html) {
        return new OutgoingMail(to, subject, text, html, List.of());
    }

    public OutgoingMail withReplyTo(String address) {
        return new OutgoingMail(to, subject, text, html, attachments, address);
    }
}