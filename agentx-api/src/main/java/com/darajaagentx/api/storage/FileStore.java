package com.darajaagentx.api.storage;

import java.util.Optional;

/**
 * Keeps small files by key. The key is chosen by this service (never by a caller), for example
 * {@code profile-pictures/3f2c...e1.jpg}. One implementation writes to a folder, which is either a local folder or a
 * network share that is mounted on the server.
 */
public interface FileStore {

    void put(String key, byte[] data);

    Optional<byte[]> get(String key);

    /** Deleting a file that is not there is not an error. */
    void delete(String key);

    /** For the health check and the log, for example "local folder /srv/agentx/files". */
    String describe();

    /** True when a file can be written and read back right now. */
    boolean healthy();
}
