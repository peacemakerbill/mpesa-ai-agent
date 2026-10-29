package com.mpesaagent.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActorRequest(@NotBlank @Size(max = 20) String actorPhone) {
}
