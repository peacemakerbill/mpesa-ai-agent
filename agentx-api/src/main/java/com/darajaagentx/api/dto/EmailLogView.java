package com.darajaagentx.api.dto;

import com.darajaagentx.api.model.EmailLog;

import java.time.Instant;

public record EmailLogView(String kind, String recipients, String subject, String status, String error, String attachment, Instant at) {

    public static EmailLogView of(EmailLog l) {
        return new EmailLogView(l.getKind(), l.getRecipients(), l.getSubject(), l.getStatus(), l.getError(), l.getAttachment(), l.getCreatedAt());
    }
}
