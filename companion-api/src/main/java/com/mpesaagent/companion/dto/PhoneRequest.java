package com.mpesaagent.companion.dto;

import jakarta.validation.constraints.NotBlank;

public record PhoneRequest(@NotBlank String phone) {
}
