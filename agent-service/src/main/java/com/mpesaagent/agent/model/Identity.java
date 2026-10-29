package com.mpesaagent.agent.model;

public record Identity(String phone, Role role, String name) {

    public boolean isStaff() {
        return role.atLeast(Role.STAFF);
    }
}
