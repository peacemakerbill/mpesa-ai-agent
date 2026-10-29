package com.mpesaagent.agent.model;

import java.time.Instant;
import java.util.Map;

/**
 * A confirmation the person has been asked for. The language model can only create one, it can never act on
 * it: the money moves when the person taps the button, and the button carries only this id.
 */
public record Intent(String id, IntentType type, String phone, Map<String, String> data, Instant createdAt) {

    public String get(String key) {
        return data == null ? null : data.get(key);
    }
}
