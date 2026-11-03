package com.darajaagentx.api.dto;

import com.darajaagentx.api.model.BusinessOperation;
import com.darajaagentx.api.model.BusinessOperationType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A business operation as the phone and the assistant see it. There are no secrets and no callback token in here.
 *
 * @param qrCode the QR picture as Base64, filled only in the answer to the call that made a Dynamic QR. It is never stored.
 */
public record BusinessOperationView(
        UUID id,
        UUID accountId,
        String accountLabel,
        String type,
        String typeLabel,
        String status,
        int amount,
        String summary,
        Map<String, String> params,
        boolean needsApproval,
        String receipt,
        String resultText,
        String failure,
        Instant createdAt,
        Instant expiresAt,
        Instant completedAt,
        String qrCode
) {

    public static BusinessOperationView of(BusinessOperation o, boolean approvalRequired, String qrCode) {
        BusinessOperationType t = o.getType();
        return new BusinessOperationView(o.getId(), o.getAccountId(), o.getAccountLabel(), t.name(), t.label(), o.getStatus().name(),
                o.getAmount(), o.getSummary(), o.getParams(), approvalRequired && o.getStatus() == com.darajaagentx.api.model.BusinessOperationStatus.PENDING_APPROVAL,
                o.getReceipt(), o.getResultText(), o.getFailure(), o.getCreatedAt(), o.getExpiresAt(), o.getCompletedAt(), qrCode);
    }
}
