package com.mpesaagent.agent.exception;

/** payment-service said no (or could not be reached, status 0). The message is safe to show a person. */
public class PaymentApiException extends RuntimeException {

    private final int status;

    public PaymentApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public boolean isNotFound() {
        return status == 404;
    }

    public boolean isForbidden() {
        return status == 403;
    }

    public boolean isUnavailable() {
        return status == 0 || status >= 500;
    }
}
