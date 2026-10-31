package com.mpesaagent.payment.exception;

/**
 * Network failure or timeout while talking to Daraja. The request may or may not have reached Safaricom,
 * so the payment must NOT be marked FAILED, and must not be pushed again automatically.
 */
@SuppressWarnings("serial")
public class MpesaCommunicationException extends RuntimeException {

    public MpesaCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
