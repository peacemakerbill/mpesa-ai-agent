package com.mpesaagent.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/** A payment as payment-service reports it. Status is one of PENDING, PUSHED, SUCCESS, FAILED, CANCELLED, TIMEOUT. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentView(
        UUID id,
        String status,
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
}
