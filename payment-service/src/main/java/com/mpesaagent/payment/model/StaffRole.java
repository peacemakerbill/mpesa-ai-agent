package com.mpesaagent.payment.model;

/**
 * Anyone who is not listed as staff is a plain customer. Roles are cumulative:
 * STAFF can look things up and request payouts, APPROVER can also approve, ADMIN can also read the
 * account balance and request reversals.
 */
public enum StaffRole {
    STAFF,
    APPROVER,
    ADMIN;

    public boolean atLeast(StaffRole minimum) {
        return this.ordinal() >= minimum.ordinal();
    }
}
