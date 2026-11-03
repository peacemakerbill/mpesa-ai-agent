package com.darajaagentx.api.dto;

import java.time.Instant;

public record LinkCodeResponse(String code, Instant expiresAt) {
}
