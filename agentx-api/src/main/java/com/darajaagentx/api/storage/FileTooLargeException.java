package com.darajaagentx.api.storage;

/** The file is bigger than this store accepts. The message is safe to show. */
@SuppressWarnings("serial")
public class FileTooLargeException extends StorageException {

    public FileTooLargeException(String message) {
        super(message);
    }
}
