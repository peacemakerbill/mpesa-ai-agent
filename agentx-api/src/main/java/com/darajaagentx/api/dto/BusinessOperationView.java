package com.darajaagentx.api.dto;

import com.darajaagentx.api.model.BusinessOperation;
import com.darajaagentx.api.model.BusinessOperationType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
        String qrCode,
        /** What happened to it, oldest first: asked, approved, sent, accepted, timed out, finished. */
        List<Step> timeline
) {

    /** One step in what happened. [code] is a short machine word, [text] the plain sentence to show. */
    public record Step(Instant at, String code, String text) {
    }

    public static BusinessOperationView of(BusinessOperation o, boolean approvalRequired, String qrCode) {
        BusinessOperationType t = o.getType();
        return new BusinessOperationView(o.getId(), o.getAccountId(), o.getAccountLabel(), t.name(), t.label(), o.getStatus().name(),
                o.getAmount(), o.getSummary(), o.getParams(), approvalRequired && o.getStatus() == com.darajaagentx.api.model.BusinessOperationStatus.PENDING_APPROVAL,
                o.getReceipt(), o.getResultText(), o.getFailure(), o.getCreatedAt(), o.getExpiresAt(), o.getCompletedAt(), qrCode, timeline(o));
    }

    /**
     * The recorded steps. A request from before steps were recorded gets a short one rebuilt from what is stored, so the
     * screen never shows an empty history.
     */
    static List<Step> timeline(BusinessOperation o) {
        List<Step> out = new ArrayList<>();
        for (BusinessOperation.Event e : o.events()) {
            out.add(new Step(e.at(), e.code(), e.text()));
        }
        if (!out.isEmpty()) {
            return out;
        }
        if (o.getCreatedAt() != null) {
            out.add(new Step(o.getCreatedAt(), "ASKED", o.getSummary() == null ? "Asked." : "Asked: " + o.getSummary()));
        }
        if (o.getApprovedAt() != null) {
            out.add(new Step(o.getApprovedAt(), "SENT", "Sent to Safaricom."));
        }
        if (o.getCompletedAt() != null) {
            String text = o.getFailure() != null && !o.getFailure().isBlank() ? o.getFailure() : o.getResultText();
            out.add(new Step(o.getCompletedAt(), o.getStatus().name(), text == null ? "" : text));
        } else if (o.getStatus() == com.darajaagentx.api.model.BusinessOperationStatus.UNKNOWN && o.getUpdatedAt() != null) {
            out.add(new Step(o.getUpdatedAt(), "UNKNOWN", o.getFailure() == null ? "" : o.getFailure()));
        }
        return out;
    }
}
