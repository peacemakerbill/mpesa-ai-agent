package com.mganji.payment.exception;

@SuppressWarnings("serial")
public class LimitExceededException extends RuntimeException {

    public LimitExceededException(String message) {
        super(message);
    }
}
