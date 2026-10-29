package com.mpesaagent.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/**
 * A refund, payout, reversal, balance or transaction lookup as payment-service reports it.
 * Status: PENDING_APPROVAL, CREATED, SUBMITTED, SUCCESS, FAILED, REJECTED, TIMEOUT.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OperationView(
        UUID id,
        String type,
        String status,
        String requestedBy,
        String approvedBy,
        String recipientPhone,
        Integer amount,
        String targetReceipt,
        UUID relatedPaymentId,
        String reason,
        String mpesaReceipt,
        String resultCode,
        String resultDesc,
        String resultData,
        Instant createdAt,
        Instant updatedAt
) {
}
