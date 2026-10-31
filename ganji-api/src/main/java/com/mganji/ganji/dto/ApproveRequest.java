package com.mganji.ganji.dto;

import jakarta.validation.constraints.NotNull;

/** How the customer confirmed on the phone. We cannot verify it, it is recorded for the audit trail. */
public record ApproveRequest(@NotNull Method method) {

    public enum Method {
        BIOMETRIC, DEVICE_CREDENTIAL
    }
}
