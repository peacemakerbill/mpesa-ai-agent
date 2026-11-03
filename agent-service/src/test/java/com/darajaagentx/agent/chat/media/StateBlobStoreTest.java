package com.darajaagentx.agent.chat.media;

import com.darajaagentx.agent.store.InMemoryStateStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StateBlobStoreTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-03T08:00:00Z"));
    private final StateBlobStore store = new StateBlobStore(new InMemoryStateStore(now::get));

    @Test
    void keepsBytesIntactAndExpires() {
        String key = UUID.randomUUID().toString();
        byte[] all = new byte[256];
        for (int i = 0; i < 256; i++) {
            all[i] = (byte) i;
        }
        store.put(key, all, Duration.ofHours(24));
        assertThat(Arrays.equals(store.get(key).get(), all)).isTrue();
        now.set(now.get().plus(Duration.ofHours(25)));
        assertThat(store.get(key).isPresent()).isEqualTo(false);
    }

    @Test
    void refusesStrangeKeys() {
        assertThatThrownBy(() -> store.get("chat:log:254712345678")).isInstanceOf(IllegalArgumentException.class);
    }
}
