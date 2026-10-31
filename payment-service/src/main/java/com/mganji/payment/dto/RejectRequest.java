package com.mganji.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectRequest(@NotBlank @Size(max = 20) String actorPhone, @Size(max = 255) String reason) {
}
