package com.mpesaagent.companion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyRequest(
        @NotBlank String phone,
        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "must be 6 digits") String code,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{8,64}$", message = "must be 8 to 64 letters, digits, - or _") String deviceId,
        @Size(max = 80) String deviceName
) {
}
