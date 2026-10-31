package com.mpesaagent.companion.exception;

@SuppressWarnings("serial")
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
