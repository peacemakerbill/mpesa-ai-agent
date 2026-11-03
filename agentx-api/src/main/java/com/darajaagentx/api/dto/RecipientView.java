package com.darajaagentx.api.dto;

import com.darajaagentx.api.model.EmailRecipient;

import java.time.Instant;
import java.util.UUID;

public record RecipientView(UUID id, String email, String name, Instant createdAt) {

    public static RecipientView of(EmailRecipient r) {
        return new RecipientView(r.getId(), r.getEmail(), r.getName(), r.getCreatedAt());
    }
}
