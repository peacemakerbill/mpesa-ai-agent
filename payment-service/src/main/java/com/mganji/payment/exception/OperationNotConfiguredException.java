package com.mganji.payment.exception;

/** An M-Pesa feature was requested but its credentials (initiator, certificate, QR merchant) are not set. */
@SuppressWarnings("serial")
public class OperationNotConfiguredException extends RuntimeException {

    public OperationNotConfiguredException(String message) {
        super(message);
    }
}
