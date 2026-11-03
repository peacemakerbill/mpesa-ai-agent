package com.darajaagentx.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Starting an account. The phone gets a 6 digit code by SMS. The email is optional and is verified later with its own code. */
public record SignupRequest(
        @NotBlank String phone,
        @NotBlank @Size(max = 60) String firstName,
        @Size(max = 60) String middleName,
        @NotBlank @Size(max = 60) String lastName,
        @Size(max = 254) String email
) {
}
