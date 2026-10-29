package com.mpesaagent.agent.store;

import com.mpesaagent.agent.TestSupport;
import com.mpesaagent.agent.model.ChatTurn;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SmallStoresTest {

    private final InMemoryStateStore state = new InMemoryStateStore();

    @Test
    void conversationHistoryStartsWithUserAndAlternates() {
        ConversationStore conversations = new ConversationStore(state, TestSupport.mapper(), TestSupport.props());
        conversations.append("254700", "assistant", "stale reply from before");
        conversations.append("254700", "user", "hi");
        conversations.append("254700", "user", "are you there?");
        conversations.append("254700", "assistant", "yes");

        List<ChatTurn> history = conversations.history("254700");

        assertThat(history).extracting(ChatTurn::role).containsExactly("user", "assistant");
        assertThat(history.get(0).text()).isEqualTo("hi\n\nare you there?");
    }

    @Test
    void historyIsCappedAtTheConfiguredSize() {
        ConversationStore conversations = new ConversationStore(state, TestSupport.mapper(), TestSupport.props());
        for (int i = 0; i < 30; i++) {
            conversations.append("254700", i % 2 == 0 ? "user" : "assistant", "m" + i);
        }
        assertThat(conversations.history("254700").size()).isLessThanOrEqualTo(20);
    }

    @Test
    void blankTurnsAreNotRemembered() {
        ConversationStore conversations = new ConversationStore(state, TestSupport.mapper(), TestSupport.props());
        conversations.append("254700", "user", "  ");
        assertThat(conversations.history("254700")).isEmpty();
    }

    @Test
    void clearingForgetsWhatWasSaid() {
        ConversationStore conversations = new ConversationStore(state, TestSupport.mapper(), TestSupport.props());
        conversations.append("254700", "user", "hello");
        assertThat(conversations.history("254700")).hasSize(1);

        conversations.clear("254700");

        assertThat(conversations.history("254700")).isEmpty();
    }

    @Test
    void dedupeAcceptsAnIdOnceUntilForgotten() {
        DedupeStore dedupe = new DedupeStore(state);
        assertThat(dedupe.firstTime("wamid.1")).isTrue();
        assertThat(dedupe.firstTime("wamid.1")).isFalse();
        dedupe.forget("wamid.1");
        assertThat(dedupe.firstTime("wamid.1")).isTrue();
    }

    @Test
    void rateLimiterAllowsTheConfiguredNumberPerMinute() {
        RateLimiter limiter = new RateLimiter(state, TestSupport.props());   // limit is 5 in test props
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.allow("254700")).as("message " + (i + 1)).isTrue();
        }
        assertThat(limiter.allow("254700")).isFalse();
        assertThat(limiter.allow("254711")).isTrue();
    }

    @Test
    void lockIsExclusiveAndReleasable() {
        PhoneLock lock = new PhoneLock(state);
        Optional<String> first = lock.acquire("254700", Duration.ZERO);
        assertThat(first).isPresent();
        assertThat(lock.acquire("254700", Duration.ZERO)).isEmpty();

        lock.release("254700", "someone-elses-token");
        assertThat(lock.acquire("254700", Duration.ZERO)).isEmpty();

        lock.release("254700", first.get());
        assertThat(lock.acquire("254700", Duration.ZERO)).isPresent();
    }
}
