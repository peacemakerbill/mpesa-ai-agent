package com.mpesaagent.agent.store;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryStateStoreTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-29T10:00:00Z"));
    private final InMemoryStateStore store = new InMemoryStateStore(now::get);

    @Test
    void setIfAbsentOnlyCreatesOnce() {
        assertThat(store.setIfAbsent("k", "a", Duration.ofMinutes(1))).isTrue();
        assertThat(store.setIfAbsent("k", "b", Duration.ofMinutes(1))).isFalse();
        assertThat(store.get("k")).contains("a");
    }

    @Test
    void valuesExpire() {
        store.set("k", "v", Duration.ofMinutes(1));
        now.set(now.get().plusSeconds(61));
        assertThat(store.get("k")).isEmpty();
        assertThat(store.setIfAbsent("k", "again", Duration.ofMinutes(1))).isTrue();
    }

    @Test
    void getAndDeleteReturnsTheValueOnlyOnce() {
        store.set("k", "v", Duration.ofMinutes(1));
        assertThat(store.getAndDelete("k")).contains("v");
        assertThat(store.getAndDelete("k")).isEmpty();
    }

    @Test
    void deleteIfEqualsProtectsSomeoneElsesLock() {
        store.set("lock", "mine", Duration.ofMinutes(1));
        assertThat(store.deleteIfEquals("lock", "theirs")).isFalse();
        assertThat(store.get("lock")).contains("mine");
        assertThat(store.deleteIfEquals("lock", "mine")).isTrue();
        assertThat(store.get("lock")).isEmpty();
    }

    @Test
    void incrementCountsAndRestartsAfterExpiry() {
        assertThat(store.increment("c", Duration.ofMinutes(1))).isEqualTo(1);
        assertThat(store.increment("c", Duration.ofMinutes(1))).isEqualTo(2);
        now.set(now.get().plusSeconds(61));
        assertThat(store.increment("c", Duration.ofMinutes(1))).isEqualTo(1);
    }

    @Test
    void listKeepsOnlyTheNewestEntries() {
        for (int i = 1; i <= 5; i++) {
            store.appendToList("l", "m" + i, 3, Duration.ofHours(1));
        }
        assertThat(store.readList("l")).containsExactly("m3", "m4", "m5");
        assertThat(store.readList("missing")).isEqualTo(List.of());
    }
}
