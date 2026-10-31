package com.mganji.agent.exception;

/** The language model API failed after retries, or returned something we cannot use. */
@SuppressWarnings("serial")
public class ModelException extends RuntimeException {
    public ModelException(String message) {
        super(message);
    }

    public ModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
