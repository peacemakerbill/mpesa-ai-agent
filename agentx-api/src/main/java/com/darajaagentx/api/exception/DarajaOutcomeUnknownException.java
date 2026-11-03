package com.darajaagentx.api.exception;

/** The request was sent but no clear answer came back. It may have gone through, so it must not be repeated on its own. */
@SuppressWarnings("serial")
public class DarajaOutcomeUnknownException extends RuntimeException {
    public DarajaOutcomeUnknownException(String message) {
        super(message);
    }
}
