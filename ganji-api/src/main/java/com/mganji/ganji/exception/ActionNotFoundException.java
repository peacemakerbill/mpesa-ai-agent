package com.mganji.ganji.exception;

@SuppressWarnings("serial")
public class ActionNotFoundException extends RuntimeException {

    public ActionNotFoundException(String message) {
        super(message);
    }
}
