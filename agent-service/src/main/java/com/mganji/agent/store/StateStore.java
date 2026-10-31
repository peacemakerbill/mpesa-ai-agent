package com.mganji.agent.store;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * The small amount of shared state the agent needs: chat history, pending confirmations, locks, counters.
 * Redis in production, memory for local development and tests.
 */
public interface StateStore {

    /** Sets the value only if the key does not exist yet. Returns true when this call created it. */
    boolean setIfAbsent(String key, String value, Duration ttl);

    void set(String key, String value, Duration ttl);

    Optional<String> get(String key);

    /** Reads and removes in one step, so two simultaneous callers cannot both get the value. */
    Optional<String> getAndDelete(String key);

    void delete(String key);

    /** Removes the key only when it still holds {@code expected}. Used to release locks safely. */
    boolean deleteIfEquals(String key, String expected);

    /** Adds one. The expiry is set when the counter is created. Returns the new value. */
    long increment(String key, Duration ttlOnCreate);

    /** Adds to the end of a list, keeps the newest {@code maxLength} entries, and refreshes the expiry. */
    void appendToList(String key, String value, int maxLength, Duration ttl);

    List<String> readList(String key);
}
