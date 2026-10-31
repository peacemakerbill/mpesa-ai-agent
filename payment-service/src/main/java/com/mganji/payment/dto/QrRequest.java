package com.mganji.payment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record QrRequest(
        @NotNull @Min(1) Integer amount,
        @NotBlank @Size(max = 12) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String accountReference
) {
}
