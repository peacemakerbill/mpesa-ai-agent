package com.darajaagentx.payment.exception;

@SuppressWarnings("serial")
public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(String message) {
        super(message);
    }
}
