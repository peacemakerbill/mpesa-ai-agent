package com.mpesaagent.payment.exception;

/** The request does not fit the operation's current state, for example approving something already approved. */
public class InvalidOperationStateException extends RuntimeException {

    public InvalidOperationStateException(String message) {
        super(message);
    }
}
