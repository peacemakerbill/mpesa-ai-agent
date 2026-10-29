package com.mpesaagent.payment.dto;

import com.mpesaagent.payment.model.Payment;
import com.mpesaagent.payment.model.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        PaymentStatus status,
        String phone,
        int amount,
        String accountReference,
        String checkoutRequestId,
        String mpesaReceipt,
        String resultCode,
        String resultDesc,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
                p.getId(),
                p.getStatus(),
                p.getPhone(),
                p.getAmount(),
                p.getAccountReference(),
                p.getCheckoutRequestId(),
                p.getMpesaReceipt(),
                p.getResultCode(),
                p.getResultDesc(),
                p.getCreatedAt(),
                p.getUpdatedAt());
    }
}
