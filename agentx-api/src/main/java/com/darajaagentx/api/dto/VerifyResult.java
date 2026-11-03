package com.darajaagentx.api.dto;

import java.time.Instant;

/** The outcome of asking Safaricom whether the saved consumer key and secret work. */
public record VerifyResult(
        boolean ok,
        String message,
        /** True when the initiator password can be turned into a security credential with the certificate on this server. */
        boolean securityCredentialReady,
        String securityCredentialMessage,
        Instant verifiedAt
) {
}
