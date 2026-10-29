package com.mpesaagent.agent.store;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Single process version of the store. Nothing survives a restart and two instances do not share it. */
@Component
@ConditionalOnProperty(name = "agent.store", havingValue = "memory")
public class InMemoryStateStore implements StateStore {

    private record Entry(Object value, Instant expiresAt) {
        boolean expired(Instant now) {
            return expiresAt != null && !expiresAt.isAfter(now);
        }
    }

    private final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();
    private final Supplier<Instant> clock;

    public InMemoryStateStore() {
        this(Instant::now);
    }

    /** Lets tests move time forward. */
    public InMemoryStateStore(Supplier<Instant> clock) {
        this.clock = clock;
    }

    private Instant expiry(Duration ttl) {
        return clock.get().plus(ttl);
    }

    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        boolean[] created = {false};
        map.compute(key, (k, existing) -> {
            if (existing == null || existing.expired(clock.get())) {
                created[0] = true;
                return new Entry(value, expiry(ttl));
            }
            return existing;
        });
        return created[0];
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        map.put(key, new Entry(value, expiry(ttl)));
    }

    @Override
    public Optional<String> get(String key) {
        Entry e = map.get(key);
        if (e == null || e.expired(clock.get()) || !(e.value() instanceof String s)) {
            return Optional.empty();
        }
        return Optional.of(s);
    }

    @Override
    public Optional<String> getAndDelete(String key) {
        Entry e = map.remove(key);
        if (e == null || e.expired(clock.get()) || !(e.value() instanceof String s)) {
            return Optional.empty();
        }
        return Optional.of(s);
    }

    @Override
    public void delete(String key) {
        map.remove(key);
    }

    @Override
    public boolean deleteIfEquals(String key, String expected) {
        boolean[] removed = {false};
        map.computeIfPresent(key, (k, e) -> {
            if (!e.expired(clock.get()) && expected.equals(e.value())) {
                removed[0] = true;
                return null;
            }
            return e;
        });
        return removed[0];
    }

    @Override
    public long increment(String key, Duration ttlOnCreate) {
        long[] result = {0};
        map.compute(key, (k, e) -> {
            if (e == null || e.expired(clock.get()) || !(e.value() instanceof Long)) {
                result[0] = 1;
                return new Entry(1L, expiry(ttlOnCreate));
            }
            result[0] = (Long) e.value() + 1;
            return new Entry(result[0], e.expiresAt());
        });
        return result[0];
    }

    @Override
    @SuppressWarnings("unchecked")
    public void appendToList(String key, String value, int maxLength, Duration ttl) {
        map.compute(key, (k, e) -> {
            List<String> list = e != null && !e.expired(clock.get()) && e.value() instanceof List<?>
                    ? new ArrayList<>((List<String>) e.value()) : new ArrayList<>();
            list.add(value);
            while (list.size() > maxLength) {
                list.remove(0);
            }
            return new Entry(list, expiry(ttl));
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> readList(String key) {
        Entry e = map.get(key);
        if (e == null || e.expired(clock.get()) || !(e.value() instanceof List<?> l)) {
            return List.of();
        }
        return List.copyOf((List<String>) l);
    }
}
