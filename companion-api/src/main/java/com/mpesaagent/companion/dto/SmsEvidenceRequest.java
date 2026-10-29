package com.mpesaagent.companion.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * What the app read from an M-Pesa message on the customer's phone, already picked apart on the phone.
 * The message text itself is never sent.
 */
public record SmsEvidenceRequest(
        @NotBlank @Pattern(regexp = "^[A-Z0-9]{10}$", message = "an M-Pesa code has 10 letters and digits") String code,
        @NotNull Kind kind,
        @Min(1) @Max(1_000_000) int amount,
        /** The name and number shown after "sent to" or "paid to". Left out for contacts, so we never learn their number. */
        @Size(max = 60) @Pattern(regexp = "^[\\p{L}\\p{N} .,'&()+/-]*$", message = "contains characters that are not allowed") String payee,
        @Size(max = 40) @Pattern(regexp = "^[\\p{L}\\p{N} ._/-]*$", message = "contains characters that are not allowed") String account,
        /** When the phone received the message, in milliseconds since 1970. */
        @Min(1) long smsAt
) {

    public enum Kind {
        SENT, PAID, BILL, AIRTIME
    }
}
