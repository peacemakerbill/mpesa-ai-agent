package com.mpesaagent.payment.event;

import com.mpesaagent.payment.model.MpesaOperation;
import com.mpesaagent.payment.model.OperationType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Published (key operation.approval) when a request needs a second person. The agent notifies the approvers. */
public record ApprovalRequestedEvent(
        UUID operationId,
        OperationType type,
        String requestedBy,
        String recipientPhone,
        Integer amount,
        String reason,
        List<String> approverPhones,
        Instant occurredAt
) {
    public static ApprovalRequestedEvent from(MpesaOperation op, List<String> approverPhones) {
        return new ApprovalRequestedEvent(
                op.getId(), op.getType(), op.getRequestedBy(), op.getRecipientPhone(),
                op.getAmount(), op.getReason(), approverPhones, Instant.now());
    }
}
