package com.mpesaagent.companion.dto;

import java.time.Instant;

public record LinkCodeResponse(String code, Instant expiresAt) {
}
