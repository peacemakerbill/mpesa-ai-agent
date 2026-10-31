package com.mganji.payment.exception;

@SuppressWarnings("serial")
public class InvalidPaymentRequestException extends RuntimeException {

    public InvalidPaymentRequestException(String message) {
        super(message);
    }
}
