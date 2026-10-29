package com.mpesaagent.payment.exception;

/** An M-Pesa feature was requested but its credentials (initiator, certificate, QR merchant) are not set. */
public class OperationNotConfiguredException extends RuntimeException {

    public OperationNotConfiguredException(String message) {
        super(message);
    }
}
