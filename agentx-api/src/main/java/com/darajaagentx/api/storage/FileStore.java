package com.darajaagentx.api.storage;

import java.time.Duration;
import java.util.Optional;

/**
 * Keeps small files by key. The key is chosen by this service (never by a caller), for example
 * {@code profile-pictures/3f2c...e1.jpg}. One implementation writes to a folder (a local folder, or a network share mounted on
 * the server), one writes to the database and one to Cloudinary.
 */
public interface FileStore {

    void put(String key, byte[] data);

    /**
     * Same as {@link #put(String, byte[])}, and the store may drop the file after {@code keepFor}. A folder keeps it
     * until someone deletes it, the database store honours the time.
     */
    default void put(String key, byte[] data, Duration keepFor) {
        put(key, data);
    }

    Optional<byte[]> get(String key);

    /** Deleting a file that is not there is not an error. */
    void delete(String key);

    /** For the health check and the log, for example "local folder /srv/agentx/files". */
    String describe();

    /** True when a file can be written and read back right now. */
    boolean healthy();
}
