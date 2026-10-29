package com.mpesaagent.payment.exception;

/** The same idempotency key was reused for a different phone, amount or account reference. */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String message) {
        super(message);
    }
}
