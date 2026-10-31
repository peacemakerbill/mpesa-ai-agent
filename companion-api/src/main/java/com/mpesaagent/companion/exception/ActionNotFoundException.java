package com.mpesaagent.companion.exception;

@SuppressWarnings("serial")
public class ActionNotFoundException extends RuntimeException {

    public ActionNotFoundException(String message) {
        super(message);
    }
}
