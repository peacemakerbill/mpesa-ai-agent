package com.darajaagentx.api.exception;

@SuppressWarnings("serial")
public class InvalidActionStateException extends RuntimeException {

    public InvalidActionStateException(String message) {
        super(message);
    }
}
