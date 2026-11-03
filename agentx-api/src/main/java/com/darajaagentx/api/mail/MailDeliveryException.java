package com.darajaagentx.api.mail;

/** The email could not be sent. The message is safe to show to the customer. */
public class MailDeliveryException extends RuntimeException {

    public MailDeliveryException(String message) {
        super(message);
    }

    public MailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
