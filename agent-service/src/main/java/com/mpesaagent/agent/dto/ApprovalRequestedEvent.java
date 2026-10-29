package com.mpesaagent.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** operation.approval: money is waiting for a second person. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApprovalRequestedEvent(
        UUID operationId,
        String type,
        String requestedBy,
        String recipientPhone,
        Integer amount,
        String reason,
        List<String> approverPhones,
        Instant occurredAt
) {
}
