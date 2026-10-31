package com.mganji.agent.exception;

/** ganji-api said no, or could not be reached (status 0). The message is safe to show a person. */
@SuppressWarnings("serial")
public class GanjiApiException extends RuntimeException {

    private final int status;

    public GanjiApiException(int status, String message) {
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
