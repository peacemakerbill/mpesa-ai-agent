package com.mganji.payment.dto;

/** role is CUSTOMER, STAFF, APPROVER or ADMIN. */
public record StaffResponse(String phone, String role, String name) {
}
