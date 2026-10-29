package com.mpesaagent.companion.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * CREATED       the agent prepared it, waiting for the customer in the app
 * APPROVED      the customer confirmed in the app (biometric or device lock)
 * HANDED_OFF    the app opened M-Pesa, the customer enters the PIN there
 * DONE_REPORTED the customer says it went through (we cannot verify this)
 * NOT_DONE      the customer says it did not go through
 * DECLINED      the customer said no in the app
 * CANCELLED     cancelled from the chat
 * EXPIRED       nobody acted in time
 */
public enum ActionStatus {
    CREATED, APPROVED, HANDED_OFF, DONE_REPORTED, NOT_DONE, DECLINED, CANCELLED, EXPIRED;

    private static final Set<ActionStatus> OPEN = EnumSet.of(CREATED, APPROVED, HANDED_OFF);

    public boolean isOpen() {
        return OPEN.contains(this);
    }

    /** Only these two can run out of time. After the hand off the customer is in M-Pesa and may take a while. */
    public boolean canExpire() {
        return this == CREATED || this == APPROVED;
    }

    public boolean canMoveTo(ActionStatus next) {
        return switch (this) {
            case CREATED -> next == APPROVED || next == DECLINED || next == CANCELLED || next == EXPIRED;
            case APPROVED -> next == HANDED_OFF || next == DONE_REPORTED || next == NOT_DONE
                    || next == DECLINED || next == CANCELLED || next == EXPIRED;
            case HANDED_OFF -> next == DONE_REPORTED || next == NOT_DONE;
            default -> false;
        };
    }
}
