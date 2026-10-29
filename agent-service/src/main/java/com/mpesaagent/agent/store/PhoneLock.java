package com.mpesaagent.agent.store;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * One message per phone number is processed at a time, so two quick messages cannot both start a payment and
 * the chat history stays in order. The lock expires on its own in case a worker dies.
 */
@Component
public class PhoneLock {

    private static final Duration HOLD = Duration.ofSeconds(120);

    private final StateStore store;

    public PhoneLock(StateStore store) {
        this.store = store;
    }

    /** Waits up to {@code wait} for the lock. Returns a token to hand back to release(), or empty on timeout. */
    public Optional<String> acquire(String phone, Duration wait) {
        String token = UUID.randomUUID().toString();
        long deadline = System.nanoTime() + wait.toNanos();
        do {
            if (store.setIfAbsent("lock:" + phone, token, HOLD)) {
                return Optional.of(token);
            }
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        } while (System.nanoTime() < deadline);
        return Optional.empty();
    }

    public void release(String phone, String token) {
        store.deleteIfEquals("lock:" + phone, token);
    }
}
