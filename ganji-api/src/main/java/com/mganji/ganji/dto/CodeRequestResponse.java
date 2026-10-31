package com.mganji.ganji.dto;

import java.time.Instant;

/** devCode is only present when code-delivery is dev (a test console). It is never returned on a real system. */
public record CodeRequestResponse(Instant expiresAt, String devCode) {
}
