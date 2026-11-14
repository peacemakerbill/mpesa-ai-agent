package com.darajaagentx.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param appHash the 11 character code the Android app sends so its SMS reader can pick up the sign-in text without any
 *                permission. Optional. A value that is not 11 letters, digits, plus or slash is ignored.
 */
public record PhoneRequest(@NotBlank String phone, String appHash) {

    public PhoneRequest(String phone) {
        this(phone, null);
    }
}
