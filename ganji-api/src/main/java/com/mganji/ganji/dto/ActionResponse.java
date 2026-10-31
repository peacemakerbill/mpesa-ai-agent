package com.mganji.ganji.dto;

import com.mganji.ganji.model.WalletAction;
import com.mganji.ganji.service.ActionSummary;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ActionResponse(
        UUID id,
        String type,
        String status,
        int amount,
        String label,
        String source,
        String summary,
        Map<String, String> details,
        List<String> warnings,
        boolean needsContact,
        String receipt,
        String verification,
        Instant approvedAt,
        Instant expiresAt,
        Instant createdAt
) {
    public static ActionResponse from(WalletAction a) {
        return new ActionResponse(a.getId(), a.getType().name(), a.getStatus().name(), a.getAmount(), a.getLabel(),
                a.getSource().name(), ActionSummary.summary(a), Map.copyOf(a.getDetails()), ActionSummary.warnings(a),
                a.getDetails().containsKey("contactName"), a.getReceipt(), a.getVerification().name(), a.getApprovedAt(), a.getExpiresAt(), a.getCreatedAt());
    }
}
