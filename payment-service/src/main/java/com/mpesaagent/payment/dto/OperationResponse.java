package com.mpesaagent.payment.dto;

import com.mpesaagent.payment.model.MpesaOperation;
import com.mpesaagent.payment.model.OperationStatus;
import com.mpesaagent.payment.model.OperationType;

import java.time.Instant;
import java.util.UUID;

public record OperationResponse(
        UUID id,
        OperationType type,
        OperationStatus status,
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
    public static OperationResponse from(MpesaOperation o) {
        return new OperationResponse(
                o.getId(), o.getType(), o.getStatus(), o.getRequestedBy(), o.getApprovedBy(),
                o.getRecipientPhone(), o.getAmount(), o.getTargetReceipt(), o.getRelatedPaymentId(),
                o.getReason(), o.getMpesaReceipt(), o.getResultCode(), o.getResultDesc(), o.getResultData(),
                o.getCreatedAt(), o.getUpdatedAt());
    }
}
