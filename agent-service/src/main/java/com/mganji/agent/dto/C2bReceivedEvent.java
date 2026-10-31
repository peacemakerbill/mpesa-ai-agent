package com.mganji.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

/** c2b.received: someone paid our Paybill or Till directly. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record C2bReceivedEvent(
        String transId,
        BigDecimal amount,
        String billRefNumber,
        String payerName,
        String transTime,
        Instant occurredAt
) {
}
