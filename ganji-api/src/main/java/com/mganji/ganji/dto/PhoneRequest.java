package com.mganji.ganji.dto;

import jakarta.validation.constraints.NotBlank;

public record PhoneRequest(@NotBlank String phone) {
}
