package com.mganji.ganji.exception;

@SuppressWarnings("serial")
public class InvalidActionRequestException extends RuntimeException {

    public InvalidActionRequestException(String message) {
        super(message);
    }
}
