package com.mpesaagent.companion.config;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * A transaction manager that starts and ends transactions without touching any database. It exists so that
 * {@code @Transactional} methods and {@code @TransactionalEventListener(AFTER_COMMIT)} listeners behave the same
 * way in MongoDB mode: events are sent only after the method finished without an error.
 */
public class NoOpTransactionManager extends AbstractPlatformTransactionManager {

    @Override
    protected Object doGetTransaction() {
        return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        // nothing to begin
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        // nothing to commit
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        // nothing to roll back
    }
}
