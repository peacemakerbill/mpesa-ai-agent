package com.mganji.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** operation.done: an operation reached SUCCESS, FAILED, REJECTED or TIMEOUT. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OperationDoneEvent(
        UUID operationId,
        String type,
        String status,
        String requestedBy,
        String approvedBy,
        String recipientPhone,
        Integer amount,
        String receipt,
        String reason,
        Map<String, String> details,
        Instant occurredAt
) {
}
