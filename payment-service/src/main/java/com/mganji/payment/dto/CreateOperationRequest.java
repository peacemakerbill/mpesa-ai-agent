package com.mganji.payment.dto;

import com.mganji.payment.model.OperationType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Asks for an M-Pesa operation on behalf of {@code actorPhone}, the phone number of whoever is chatting.
 * What is allowed depends on that person's role, which payment-service decides, never the caller.
 * Fields that do not apply to the chosen type are ignored.
 */
public record CreateOperationRequest(
        @NotBlank @Size(max = 100) String idempotencyKey,
        @NotNull OperationType type,
        @NotBlank @Size(max = 20) String actorPhone,
        @Min(1) Integer amount,
        @Size(max = 20) String recipientPhone,
        @Size(max = 30) @Pattern(regexp = "^[A-Za-z0-9]*$", message = "targetReceipt must be letters and digits only")
        String targetReceipt,
        UUID relatedPaymentId,
        @Size(max = 255) String reason
) {
}
