package com.darajaagentx.api.storage;

/** The storage could not be used. The message is safe to show. */
@SuppressWarnings("serial")
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
