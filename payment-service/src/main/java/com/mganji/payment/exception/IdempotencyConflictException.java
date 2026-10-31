package com.mganji.payment.exception;

/** The same idempotency key was reused for a different phone, amount or account reference. */
@SuppressWarnings("serial")
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String message) {
        super(message);
    }
}
