package com.mpesaagent.agent.exception;

/** A chat file could not be found or was refused. */
@SuppressWarnings("serial")
public class ChatException extends RuntimeException {

    private final int status;

    public ChatException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public boolean isNotFound() {
        return status == 404;
    }

    public boolean isTooLarge() {
        return status == 413;
    }
}
