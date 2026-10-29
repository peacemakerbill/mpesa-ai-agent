package com.mpesaagent.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/** wallet.action: the customer finished, skipped or declined a request in the app. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WalletActionEvent(
        UUID actionId,
        String phone,
        String type,
        String status,
        int amount,
        String summary,
        String receipt,
        String verification,
        Instant occurredAt
) {
}
