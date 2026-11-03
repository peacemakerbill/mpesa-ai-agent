package com.darajaagentx.api.exception;

@SuppressWarnings("serial")
public class AuthFailedException extends RuntimeException {

    public AuthFailedException(String message) {
        super(message);
    }
}
