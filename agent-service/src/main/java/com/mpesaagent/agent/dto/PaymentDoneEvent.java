package com.mpesaagent.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/** payment.done: a payment reached SUCCESS, FAILED, CANCELLED or TIMEOUT. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentDoneEvent(
        UUID paymentId,
        String phone,
        String status,
        int amount,
        String accountReference,
        String receipt,
        String resultCode,
        String reason,
        Instant occurredAt
) {
}
