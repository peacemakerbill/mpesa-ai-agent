package com.mpesaagent.payment.event;

import com.mpesaagent.payment.model.Payment;
import com.mpesaagent.payment.model.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to RabbitMQ (exchange payments.events, routing key payment.done) when a payment reaches
 * SUCCESS, FAILED, CANCELLED or TIMEOUT. The agent-service listens and tells the customer in the chat.
 */
public record PaymentCompletedEvent(
        UUID paymentId,
        String phone,
        PaymentStatus status,
        int amount,
        String accountReference,
        String receipt,
        String resultCode,
        String reason,
        Instant occurredAt
) {
    public static PaymentCompletedEvent from(Payment p) {
        return new PaymentCompletedEvent(
                p.getId(),
                p.getPhone(),
                p.getStatus(),
                p.getAmount(),
                p.getAccountReference(),
                p.getMpesaReceipt(),
                p.getResultCode(),
                p.getResultDesc(),
                Instant.now());
    }
}
