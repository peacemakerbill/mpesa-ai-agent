package com.darajaagentx.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The address to verify. A 6 digit code is sent to it. */
public record EmailChangeRequest(@NotBlank @Size(max = 254) String email) {
}
