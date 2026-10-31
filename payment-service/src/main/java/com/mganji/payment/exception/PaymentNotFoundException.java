package com.mganji.payment.exception;

@SuppressWarnings("serial")
public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(String message) {
        super(message);
    }
}
