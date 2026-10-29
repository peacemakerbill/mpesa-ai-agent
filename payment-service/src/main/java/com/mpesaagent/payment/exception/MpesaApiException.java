package com.mpesaagent.payment.exception;

/**
 * Daraja answered with an error (or we could not authenticate), so we know the request was NOT processed
 * and the payment can safely be marked FAILED.
 */
public class MpesaApiException extends RuntimeException {

    private final String code;

    public MpesaApiException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
