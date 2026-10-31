package com.mganji.payment.model;

/**
 * Lifecycle of a payment.
 *
 * <pre>
 * PENDING  saved locally, STK Push not yet confirmed by Daraja
 * PUSHED   Daraja accepted the request, customer has the PIN prompt
 * SUCCESS  Safaricom confirmed the payment (final)
 * FAILED   Safaricom or Daraja rejected it (final)
 * CANCELLED customer dismissed the prompt (final)
 * TIMEOUT  no answer in time (NOT final: a late callback can still turn it into SUCCESS)
 * </pre>
 */
public enum PaymentStatus {
    PENDING,
    PUSHED,
    SUCCESS,
    FAILED,
    CANCELLED,
    TIMEOUT;

    /** A final status never changes again, so duplicate or late callbacks are ignored. */
    public boolean isFinal() {
        return this == SUCCESS || this == FAILED || this == CANCELLED;
    }

    /** Maps the M-Pesa ResultCode (callback or STK query) to our status. */
    public static PaymentStatus fromResultCode(String resultCode) {
        if (resultCode == null) {
            return FAILED;
        }
        return switch (resultCode.trim()) {
            case "0" -> SUCCESS;
            case "1032" -> CANCELLED;   // request cancelled by user
            case "1037" -> TIMEOUT;     // customer did not respond to the prompt
            default -> FAILED;          // 1 insufficient funds, 2001 wrong PIN, and others
        };
    }
}
