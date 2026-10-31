package com.mganji.payment.exception;

/** The request does not fit the operation's current state, for example approving something already approved. */
@SuppressWarnings("serial")
public class InvalidOperationStateException extends RuntimeException {

    public InvalidOperationStateException(String message) {
        super(message);
    }
}
