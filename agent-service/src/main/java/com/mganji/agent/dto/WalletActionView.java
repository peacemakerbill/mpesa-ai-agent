package com.mganji.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A request prepared for the customer's own M-Pesa, as ganji-api reports it.
 * Verification: NONE, REPORTED (customer said so), SMS_AMOUNT or SMS_MATCHED (a message on their own phone matched). Never from Safaricom.
 * Status: CREATED, APPROVED, HANDED_OFF, DONE_REPORTED, NOT_DONE, DECLINED, CANCELLED, EXPIRED.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WalletActionView(
        UUID id,
        String type,
        String status,
        int amount,
        String label,
        String source,
        String summary,
        List<String> warnings,
        boolean needsContact,
        String receipt,
        String verification,
        Instant expiresAt,
        Instant createdAt
) {
}
