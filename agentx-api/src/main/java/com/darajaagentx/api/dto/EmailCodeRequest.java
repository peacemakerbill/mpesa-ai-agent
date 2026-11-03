package com.darajaagentx.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** The 6 digit code that was emailed. */
public record EmailCodeRequest(@NotBlank @Pattern(regexp = "^\\d{6}$", message = "must be 6 digits") String code) {
}
