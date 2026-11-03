package com.darajaagentx.api.service;

import com.darajaagentx.api.model.BusinessOperation;

/** Told when a business request reaches its end (done, failed, declined, expired), for example to send an email notice. */
public interface OperationFinishedHook {

    /** Must not throw. It runs after the result is saved. */
    void finished(BusinessOperation operation);
}
