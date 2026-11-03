package com.darajaagentx.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One address to keep. [name] is what the customer calls this person, for example "Accountant". */
public record RecipientRequest(@NotBlank @Size(max = 254) String email, @Size(max = 80) String name) {
}
