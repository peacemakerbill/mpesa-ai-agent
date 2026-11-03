package com.darajaagentx.api.exception;

@SuppressWarnings("serial")
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
