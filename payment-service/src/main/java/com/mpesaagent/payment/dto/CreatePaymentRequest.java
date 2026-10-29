package com.mpesaagent.payment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Sent by the agent-service after the customer taps the Confirm button.
 * The idempotency key should be unique per confirmation (the chat message id works well),
 * so a retried request never charges twice.
 */
public record CreatePaymentRequest(
        @NotBlank @Size(max = 100) String idempotencyKey,
        @NotBlank @Size(max = 20) String phone,
        @NotNull @Min(1) Integer amount,
        @NotBlank
        @Size(max = 12, message = "accountReference can be at most 12 characters (Daraja limit)")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "accountReference may contain letters, digits, '-' and '_' only")
        String accountReference
) {
}
