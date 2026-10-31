package com.mganji.payment.model;

/**
 * PENDING_APPROVAL waiting for a second person, CREATED approved and about to be sent to Daraja,
 * SUBMITTED Daraja accepted it and the result will arrive by callback, SUCCESS and FAILED are results,
 * REJECTED an approver said no, TIMEOUT Safaricom's queue timed out (a late result can still arrive).
 */
public enum OperationStatus {
    PENDING_APPROVAL,
    CREATED,
    SUBMITTED,
    SUCCESS,
    FAILED,
    REJECTED,
    TIMEOUT;

    public boolean isFinal() {
        return this == SUCCESS || this == FAILED || this == REJECTED;
    }
}
