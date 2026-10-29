package com.mpesaagent.companion.event;

import java.time.Instant;
import java.util.UUID;

/** wallet.action: the customer finished, skipped or declined a request, or a phone message backed one up. */
public record ActionEvent(UUID actionId, String phone, String type, String status, int amount, String summary,
                          String receipt, String verification, Instant occurredAt) {
}
