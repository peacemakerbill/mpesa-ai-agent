package com.mganji.agent.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mganji.agent.store.StateStore;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Files in a chat, in both directions: what the customer attaches and what the assistant sends back (receipts,
 * voice replies). Each file belongs to one phone number and is kept for 24 hours. Held in the state store, so
 * this is meant for chat-sized files, not an archive.
 */
@Component
public class ChatMedia {

    static final Duration KEEP = Duration.ofHours(24);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Stored(String phone, String mimeType, String fileName, String data) {
    }

    private final StateStore store;
    private final ObjectMapper mapper;

    public ChatMedia(StateStore store, ObjectMapper mapper) {
        this.store = store;
        this.mapper = mapper;
    }

    /** Stores the file and returns its id. */
    public String put(String phone, byte[] bytes, String mimeType, String fileName) {
        String id = UUID.randomUUID().toString();
        Stored stored = new Stored(phone, mimeType == null || mimeType.isBlank() ? "application/octet-stream" : mimeType,
                fileName, Base64.getEncoder().encodeToString(bytes));
        try {
            store.set("chat:media:" + id, mapper.writeValueAsString(stored), KEEP);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not store a file", e);
        }
        return id;
    }

    /** The file, only when it belongs to this phone number. */
    public Optional<Found> get(String phone, String id) {
        if (id == null || !id.matches("^[0-9a-fA-F-]{36}$")) {
            return Optional.empty();
        }
        return store.get("chat:media:" + id).flatMap(json -> {
            try {
                Stored s = mapper.readValue(json, Stored.class);
                if (!phone.equals(s.phone())) {
                    return Optional.empty();
                }
                return Optional.of(new Found(Base64.getDecoder().decode(s.data()), s.mimeType(), s.fileName()));
            } catch (JacksonException | IllegalArgumentException e) {
                return Optional.empty();
            }
        });
    }

    public boolean exists(String phone, String id) {
        return get(phone, id).isPresent();
    }

    public record Found(byte[] bytes, String mimeType, String fileName) {
    }
}