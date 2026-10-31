package com.mganji.payment.event;

import com.mganji.payment.model.C2bTransaction;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Published (key c2b.received) for each direct Paybill or Till payment. The agent can thank the customer
 * in the chat when billRefNumber is their phone number.
 */
public record C2bReceivedEvent(
        String transId,
        BigDecimal amount,
        String billRefNumber,
        String payerName,
        String transTime,
        Instant occurredAt
) {
    public static C2bReceivedEvent from(C2bTransaction t) {
        return new C2bReceivedEvent(
                t.getTransId(), t.getAmount(), t.getBillRefNumber(), t.getPayerName(), t.getTransTime(), Instant.now());
    }
}
