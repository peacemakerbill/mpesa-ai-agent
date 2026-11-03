package com.darajaagentx.api.model;

/** Where a business operation is. SUBMITTED means Safaricom accepted it and the final answer has not arrived yet. */
public enum BusinessOperationStatus {
    PENDING_APPROVAL,
    SUBMITTED,
    SUCCEEDED,
    FAILED,
    DECLINED,
    EXPIRED,
    /** The call may or may not have reached Safaricom. Nothing is retried, the owner has to check M-Pesa first. */
    UNKNOWN;

    public boolean finished() {
        return this == SUCCEEDED || this == FAILED || this == DECLINED || this == EXPIRED;
    }
}
