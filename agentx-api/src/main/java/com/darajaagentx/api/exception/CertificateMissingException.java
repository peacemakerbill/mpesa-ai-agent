package com.darajaagentx.api.exception;

/** There is no Safaricom public certificate for the environment, so a security credential cannot be made. */
@SuppressWarnings("serial")
public class CertificateMissingException extends RuntimeException {

    public CertificateMissingException(String message) {
        super(message);
    }
}
