package com.mpesaagent.companion.exception;

@SuppressWarnings("serial")
public class InvalidActionStateException extends RuntimeException {

    public InvalidActionStateException(String message) {
        super(message);
    }
}
