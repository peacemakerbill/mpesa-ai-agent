package com.mpesaagent.payment.event;

import com.mpesaagent.payment.model.MpesaOperation;
import com.mpesaagent.payment.model.OperationStatus;
import com.mpesaagent.payment.model.OperationType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Published (exchange payments.events, key operation.done) when an operation reaches SUCCESS, FAILED,
 * REJECTED or TIMEOUT. {@code details} carries the readable result, for example the account balances
 * or the transaction status fields, so the agent can answer the person who asked.
 */
public record OperationCompletedEvent(
        UUID operationId,
        OperationType type,
        OperationStatus status,
        String requestedBy,
        String approvedBy,
        String recipientPhone,
        Integer amount,
        String receipt,
        String reason,
        Map<String, String> details,
        Instant occurredAt
) {
    public static OperationCompletedEvent from(MpesaOperation op, Map<String, String> details) {
        return new OperationCompletedEvent(
                op.getId(), op.getType(), op.getStatus(), op.getRequestedBy(), op.getApprovedBy(),
                op.getRecipientPhone(), op.getAmount(), op.getMpesaReceipt(), op.getResultDesc(),
                details == null ? Map.of() : details, Instant.now());
    }
}
