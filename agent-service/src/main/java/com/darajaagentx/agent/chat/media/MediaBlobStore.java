package com.darajaagentx.agent.chat.media;

import java.time.Duration;
import java.util.Optional;

/**
 * Where the bytes of chat files live. Keys are plain ids (a UUID, or a UUID with a short suffix); nothing else is allowed,
 * so a key can never point outside the store.
 *
 * <ul>
 *   <li>{@code state}: the agent's state store (Redis). Short lived. This is how the chat worked before.</li>
 *   <li>{@code local}: a folder on the server, or on a disk or network share mounted there (NFS, SMB, EFS).</li>
 *   <li>{@code s3}: any S3 compatible service over the network: AWS S3, MinIO, Cloudflare R2, DigitalOcean Spaces, Wasabi.</li>
 * </ul>
 */
public interface MediaBlobStore {

    /** Short name used in logs and settings. */
    String kind();

    /** How long files are kept when {@code chat.media.retention-hours} is not set. */
    Duration defaultRetention();

    /** Stores the bytes under the key, replacing anything already there. The store may drop it after {@code keepFor}. */
    void put(String key, byte[] bytes, Duration keepFor);

    Optional<byte[]> get(String key);

    void delete(String key);

    /** Keys are ids we made ourselves. Anything else is refused before it reaches a disk or a URL. */
    static void requireSafeKey(String key) {
        if (key == null || !key.matches("^[0-9a-fA-F-]{36}(\\.meta)?$")) {
            throw new IllegalArgumentException("Not a valid media key");
        }
    }
}
