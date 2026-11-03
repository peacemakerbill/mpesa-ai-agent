package com.darajaagentx.agent.chat.media;

import com.darajaagentx.agent.store.StateStore;

import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/** Keeps files in the state store, as base64, for a day. Fine for chat sized files, not an archive. */
public class StateBlobStore implements MediaBlobStore {

    private static final String PREFIX = "chat:media:";

    private final StateStore store;

    public StateBlobStore(StateStore store) {
        this.store = store;
    }

    @Override
    public String kind() {
        return "state";
    }

    @Override
    public Duration defaultRetention() {
        return Duration.ofHours(24);
    }

    @Override
    public void put(String key, byte[] bytes, Duration keepFor) {
        MediaBlobStore.requireSafeKey(key);
        store.set(PREFIX + key, Base64.getEncoder().encodeToString(bytes), keepFor);
    }

    @Override
    public Optional<byte[]> get(String key) {
        MediaBlobStore.requireSafeKey(key);
        return store.get(PREFIX + key).flatMap(v -> {
            try {
                return Optional.of(Base64.getDecoder().decode(v));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        });
    }

    @Override
    public void delete(String key) {
        MediaBlobStore.requireSafeKey(key);
        store.delete(PREFIX + key);
    }
}
