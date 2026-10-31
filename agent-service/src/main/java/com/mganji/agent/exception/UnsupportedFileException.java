package com.mganji.agent.exception;

/** A file we cannot read. The message is written for the customer. */
@SuppressWarnings("serial")
public class UnsupportedFileException extends RuntimeException {
    public UnsupportedFileException(String message) {
        super(message);
    }

    public UnsupportedFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
