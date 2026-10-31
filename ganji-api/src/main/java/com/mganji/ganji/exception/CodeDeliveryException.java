package com.mganji.ganji.exception;

/** The sign-in code could not be delivered. */
@SuppressWarnings("serial")
public class CodeDeliveryException extends RuntimeException {

    public CodeDeliveryException(String message) {
        super(message);
    }
}
