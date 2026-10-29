package com.mpesaagent.companion.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/** In memory rate limit for the public login endpoint. One instance per service copy, which is enough here. */
@Component
public class SlidingWindowLimiter {

    private final ConcurrentHashMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final Clock clock;

    public SlidingWindowLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean allow(String key, int max, Duration window) {
        long now = clock.millis();
        long cutoff = now - window.toMillis();
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() < cutoff) {
                q.pollFirst();
            }
            if (q.size() >= max) {
                return false;
            }
            q.addLast(now);
            return true;
        }
    }

    /** Called now and then so the map does not grow without limit. */
    public void sweep(Duration window) {
        long cutoff = clock.millis() - window.toMillis();
        hits.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().isEmpty() || e.getValue().peekLast() < cutoff;
            }
        });
    }
}
