package com.darajaagentx.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * How the customer confirmed. BIOMETRIC and DEVICE_CREDENTIAL are checked by the phone and cannot be verified here,
 * they are recorded for the audit trail. OTP is checked here: the code sent by text message must match.
 */
public record ApproveRequest(@NotNull Method method, @Size(max = 12) String code) {

    public enum Method {
        BIOMETRIC, DEVICE_CREDENTIAL, OTP
    }

    public ApproveRequest(Method method) {
        this(method, null);
    }
}
