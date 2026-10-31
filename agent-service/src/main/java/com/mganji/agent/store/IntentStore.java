package com.mganji.agent.store;

import com.mganji.agent.model.Intent;
import com.mganji.agent.model.IntentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pending confirmations. A button only carries the intent id; everything that matters (amount, recipient)
 * lives here, server side, so a customer cannot change an amount by editing a message and the language model
 * cannot confirm anything on a customer's behalf. An intent can be used once, only by the phone it was made for.
 */
@Component
public class IntentStore {

    private static final Logger log = LoggerFactory.getLogger(IntentStore.class);
    private static final String PREFIX = "intent:";

    private final StateStore store;
    private final ObjectMapper mapper;

    public IntentStore(StateStore store, ObjectMapper mapper) {
        this.store = store;
        this.mapper = mapper;
    }

    public Intent create(IntentType type, String phone, Map<String, String> data, Duration ttl) {
        Intent intent = new Intent(UUID.randomUUID().toString().replace("-", ""), type, phone, Map.copyOf(data), Instant.now());
        try {
            store.set(PREFIX + intent.id(), mapper.writeValueAsString(intent), ttl);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not store the confirmation", e);
        }
        return intent;
    }

    /**
     * Returns the intent and removes it, but only when it belongs to {@code phone} and has the expected type.
     * A tap from another number, or of the wrong kind, leaves it untouched.
     */
    public Optional<Intent> take(String intentId, String phone, IntentType... allowed) {
        if (intentId == null || !intentId.matches("^[a-f0-9]{32}$")) {
            return Optional.empty();
        }
        Optional<Intent> peeked = read(store.get(PREFIX + intentId));
        if (peeked.isEmpty()) {
            return Optional.empty();
        }
        Intent intent = peeked.get();
        boolean typeOk = allowed.length == 0 || java.util.Arrays.asList(allowed).contains(intent.type());
        if (!intent.phone().equals(phone) || !typeOk) {
            log.warn("Intent {} was used by a different phone or for a different action", intentId);
            return Optional.empty();
        }
        // The removal is the real gate: if two taps race, only one of them gets the value
        return read(store.getAndDelete(PREFIX + intentId));
    }

    /** Cancels without returning anything. Only the owner can cancel. */
    public boolean discard(String intentId, String phone) {
        Optional<Intent> intent = take(intentId, phone);
        return intent.isPresent();
    }

    private Optional<Intent> read(Optional<String> json) {
        if (json.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(json.get(), Intent.class));
        } catch (JacksonException e) {
            log.error("Unreadable stored intent", e);
            return Optional.empty();
        }
    }
}