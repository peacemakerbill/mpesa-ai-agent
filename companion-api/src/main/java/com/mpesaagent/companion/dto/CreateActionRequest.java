package com.mpesaagent.companion.dto;

import com.mpesaagent.companion.model.ActionSource;
import com.mpesaagent.companion.model.ActionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/** Sent by agent-service. The idempotency key makes a retried request return the same action. */
public record CreateActionRequest(
        @NotBlank @Size(max = 100) String idempotencyKey,
        @NotBlank String phone,
        @NotNull ActionType type,
        @NotNull Integer amount,
        Map<String, String> details,
        ActionSource source,
        @Size(max = 80) String label
) {
}
