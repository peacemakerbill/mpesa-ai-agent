package com.mganji.agent.store;

import com.mganji.agent.TestSupport;
import com.mganji.agent.model.Intent;
import com.mganji.agent.model.IntentType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class IntentStoreTest {

    private static final String PHONE = "254712345678";
    private static final String OTHER = "254722000000";

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-29T10:00:00Z"));
    private final IntentStore intents = new IntentStore(new InMemoryStateStore(now::get), TestSupport.mapper());

    private Intent payment() {
        return intents.create(IntentType.PAYMENT, PHONE, Map.of("amount", "500", "reference", "ORD-1"), Duration.ofMinutes(10));
    }

    @Test
    void ownerCanTakeItExactlyOnce() {
        Intent intent = payment();

        Optional<Intent> first = intents.take(intent.id(), PHONE, IntentType.PAYMENT);
        assertThat(first).isPresent();
        assertThat(first.get().get("amount")).isEqualTo("500");
        assertThat(intents.take(intent.id(), PHONE, IntentType.PAYMENT)).isEmpty();
    }

    @Test
    void someoneElsesTapDoesNothingAndDoesNotBurnTheIntent() {
        Intent intent = payment();

        assertThat(intents.take(intent.id(), OTHER, IntentType.PAYMENT)).isEmpty();
        assertThat(intents.take(intent.id(), PHONE, IntentType.PAYMENT)).isPresent();
    }

    @Test
    void wrongKindOfConfirmationIsRefused() {
        Intent intent = payment();

        assertThat(intents.take(intent.id(), PHONE, IntentType.REFUND, IntentType.PAYOUT)).isEmpty();
        assertThat(intents.take(intent.id(), PHONE, IntentType.PAYMENT)).isPresent();
    }

    @Test
    void expiredAndMadeUpIdsAreRefused() {
        Intent intent = payment();
        now.set(now.get().plus(Duration.ofMinutes(11)));

        assertThat(intents.take(intent.id(), PHONE)).isEmpty();
        assertThat(intents.take("0".repeat(32), PHONE)).isEmpty();
        assertThat(intents.take("../etc/passwd", PHONE)).isEmpty();
        assertThat(intents.take(null, PHONE)).isEmpty();
    }

    @Test
    void discardRemovesOnlyForTheOwner() {
        Intent intent = payment();

        assertThat(intents.discard(intent.id(), OTHER)).isFalse();
        assertThat(intents.discard(intent.id(), PHONE)).isTrue();
        assertThat(intents.take(intent.id(), PHONE)).isEmpty();
    }

    @Test
    void idsAreUnpredictable() {
        assertThat(payment().id()).isNotEqualTo(payment().id()).hasSize(32);
    }
}
