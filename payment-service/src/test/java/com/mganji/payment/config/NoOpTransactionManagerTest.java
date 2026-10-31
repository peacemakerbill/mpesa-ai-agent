package com.mganji.payment.config;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoOpTransactionManagerTest {

    private final TransactionTemplate template = new TransactionTemplate(new NoOpTransactionManager());

    private static TransactionSynchronization recording(List<String> seen) {
        return new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                seen.add("afterCommit");
            }

            @Override
            public void afterCompletion(int status) {
                seen.add(status == STATUS_COMMITTED ? "committed" : "rolledBack");
            }
        };
    }

    @Test
    void afterCommitCallbacksRunWhenTheMethodFinishes() {
        List<String> seen = new ArrayList<>();

        template.executeWithoutResult(status -> TransactionSynchronizationManager.registerSynchronization(recording(seen)));

        assertThat(seen).containsExactly("afterCommit", "committed");
    }

    @Test
    void afterCommitCallbacksDoNotRunWhenTheMethodFails() {
        List<String> seen = new ArrayList<>();

        assertThatThrownBy(() -> template.executeWithoutResult(status -> {
            TransactionSynchronizationManager.registerSynchronization(recording(seen));
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(seen).containsExactly("rolledBack");
    }
}
