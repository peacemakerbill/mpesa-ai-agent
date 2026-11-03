package com.darajaagentx.api.dto;

import jakarta.validation.constraints.NotBlank;

public record PhoneRequest(@NotBlank String phone) {
}
