package com.mpesaagent.agent.exception;

/** companion-api said no, or could not be reached (status 0). The message is safe to show a person. */
public class CompanionApiException extends RuntimeException {

    private final int status;

    public CompanionApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public boolean isNotFound() {
        return status == 404;
    }

    public boolean isConflict() {
        return status == 409;
    }

    public boolean isTooManyRequests() {
        return status == 429;
    }

    public boolean isUnavailable() {
        return status == 0 || status >= 500;
    }
}
