package com.darajaagentx.api.dto;

import java.time.Instant;

/**
 * The answer to "send me a code to approve this".
 *
 * @param expiresAt          when the code stops working
 * @param resendAfterSeconds how long the app should wait before offering "send again"
 * @param devCode            the code itself, only where the app is allowed to show codes (never in production)
 */
public record ApprovalCodeResponse(Instant expiresAt, int resendAfterSeconds, String devCode) {
}
