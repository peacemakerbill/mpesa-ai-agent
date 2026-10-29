package com.mpesaagent.payment.controller;

import com.mpesaagent.payment.dto.StaffResponse;
import com.mpesaagent.payment.service.StaffService;
import com.mpesaagent.payment.util.PhoneNumberUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lets the agent know which tools to offer a chat partner. The real checks still happen in payment-service. */
@RestController
@RequestMapping("/internal/staff")
public class InternalStaffController {

    private final StaffService staff;

    public InternalStaffController(StaffService staff) {
        this.staff = staff;
    }

    @GetMapping("/{phone}")
    public StaffResponse role(@PathVariable String phone) {
        String normalized = PhoneNumberUtil.normalize(phone);
        return staff.find(normalized)
                .map(m -> new StaffResponse(normalized, m.role().name(), m.name()))
                .orElseGet(() -> new StaffResponse(normalized, "CUSTOMER", ""));
    }
}
