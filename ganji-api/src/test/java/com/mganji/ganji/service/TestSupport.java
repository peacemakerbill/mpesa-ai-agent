package com.mganji.ganji.service;

import com.mganji.ganji.config.GanjiProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

/** Shared helpers for the unit tests. */
final class TestSupport {

    static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");

    private TestSupport() {
    }

    static Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    static GanjiProperties propsWithDelivery(String codeDelivery) {
        return new GanjiProperties("test-token-secret-with-at-least-32-characters", Duration.ofDays(30), Duration.ofMinutes(10),
                5, 3, Duration.ofMinutes(30), 10, 250_000, 5, "https://app.example.test", "", "", "", codeDelivery,
                new GanjiProperties.HandOff("*334#", "", "", Map.of()));
    }

    static GanjiProperties props() {
        return new GanjiProperties("test-token-secret-with-at-least-32-characters", Duration.ofDays(30), Duration.ofMinutes(10),
                5, 3, Duration.ofMinutes(30), 10, 250_000, 5, "https://app.example.test", "", "", "", "",
                new GanjiProperties.HandOff("*334#", "", "", Map.of()));
    }
}
