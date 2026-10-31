package com.mganji.agent.model;

/** Who is chatting. Each staff role includes everything the lower ones can do. */
public enum Role {
    CUSTOMER, STAFF, APPROVER, ADMIN;

    public boolean atLeast(Role other) {
        return this.ordinal() >= other.ordinal();
    }

    public static Role parse(String value) {
        if (value == null) {
            return CUSTOMER;
        }
        try {
            return Role.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return CUSTOMER;
        }
    }
}
