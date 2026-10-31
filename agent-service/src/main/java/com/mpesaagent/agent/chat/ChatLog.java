package com.mpesaagent.agent.chat;

import com.mpesaagent.agent.store.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Each customer's chat, kept in the shared state store (Redis, or memory in development): the newest messages in
 * both directions, numbered so an app can ask for "everything after 41". It also holds the "typing" flag.
 */
@Component
public class ChatLog {

    private static final Logger log = LoggerFactory.getLogger(ChatLog.class);
    static final int KEEP = 300;
    static final Duration RETENTION = Duration.ofDays(30);
    static final Duration TYPING = Duration.ofSeconds(25);

    private final StateStore store;
    private final ObjectMapper mapper;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public ChatLog(StateStore store, ObjectMapper mapper) {
        this.store = store;
        this.mapper = mapper;
    }

    /** Numbers the message, stamps the time and stores it. A reply also ends the "typing" state. */
    public ChatMessage append(String phone, ChatMessage message) {
        // One writer per phone keeps the numbers and the stored order the same on a single instance
        synchronized (locks.computeIfAbsent(phone, p -> new Object())) {
            message.id = store.increment("chat:seq:" + phone, RETENTION);
            message.at = Instant.now().toEpochMilli();
            try {
                store.appendToList("chat:log:" + phone, mapper.writeValueAsString(message), KEEP, RETENTION);
            } catch (JacksonException e) {
                throw new IllegalStateException("Could not store a chat message", e);
            }
        }
        if ("out".equals(message.dir)) {
            store.delete("chat:typing:" + phone);
        }
        return message;
    }

    /** Messages numbered above {@code after}, oldest first, at most {@code limit}. */
    public List<ChatMessage> after(String phone, long after, int limit) {
        List<ChatMessage> result = new ArrayList<>();
        for (String json : store.readList("chat:log:" + phone)) {
            try {
                ChatMessage m = mapper.readValue(json, ChatMessage.class);
                if (m.id > after) {
                    result.add(m);
                }
            } catch (JacksonException e) {
                log.warn("Skipping an unreadable chat message");
            }
        }
        result.sort((a, b) -> Long.compare(a.id, b.id));
        return result.size() <= limit ? result : new ArrayList<>(result.subList(0, limit));
    }

    /** The number of the newest message, or 0. */
    public long lastId(String phone) {
        return store.get("chat:seq:" + phone).map(s -> {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                return 0L;
            }
        }).orElse(0L);
    }

    public void typing(String phone, boolean on) {
        if (on) {
            store.set("chat:typing:" + phone, "1", TYPING);
        } else {
            store.delete("chat:typing:" + phone);
        }
    }

    public boolean isTyping(String phone) {
        return store.get("chat:typing:" + phone).isPresent();
    }

    /** Starts the chat over. Message numbers keep counting so an open app notices the change. */
    public void clear(String phone) {
        store.delete("chat:log:" + phone);
        store.delete("chat:typing:" + phone);
    }
}