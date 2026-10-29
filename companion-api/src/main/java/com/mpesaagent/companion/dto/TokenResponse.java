package com.mpesaagent.companion.dto;

import java.time.Instant;

public record TokenResponse(String token, String phone, Instant expiresAt) {
}
