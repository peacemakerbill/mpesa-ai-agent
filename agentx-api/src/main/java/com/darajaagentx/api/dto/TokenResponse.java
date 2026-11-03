package com.darajaagentx.api.dto;

import java.time.Instant;

public record TokenResponse(String token, String phone, Instant expiresAt) {
}
