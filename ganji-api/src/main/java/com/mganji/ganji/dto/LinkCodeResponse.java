package com.mganji.ganji.dto;

import java.time.Instant;

public record LinkCodeResponse(String code, Instant expiresAt) {
}
