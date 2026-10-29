package com.mpesaagent.companion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReceiptRequest(@NotBlank String phone, @NotBlank @Size(max = 30) String receipt) {
}
