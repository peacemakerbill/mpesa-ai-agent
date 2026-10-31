package com.mganji.payment.exception;

/** The person is not allowed to do this, for example a customer asking for the account balance. */
@SuppressWarnings("serial")
public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}
