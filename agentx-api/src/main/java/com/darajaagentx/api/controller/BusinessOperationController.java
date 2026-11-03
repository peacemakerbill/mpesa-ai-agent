package com.darajaagentx.api.controller;

import com.darajaagentx.api.config.DeviceAuthFilter;
import com.darajaagentx.api.config.DevicePrincipal;
import com.darajaagentx.api.dto.ApproveRequest;
import com.darajaagentx.api.dto.BusinessOperationView;
import com.darajaagentx.api.model.BusinessOperation;
import com.darajaagentx.api.service.BusinessOperationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * What the assistant prepared on the customer's business accounts. The phone sees each one, approves or declines it,
 * and reads the result. The customer is the one in the login token.
 */
@RestController
@RequestMapping("/api/business-operations")
public class BusinessOperationController {

    private final BusinessOperationService service;

    public BusinessOperationController(BusinessOperationService service) {
        this.service = service;
    }

    @GetMapping
    public List<BusinessOperationView> list(@RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return service.list(who.phone()).stream().map(this::view).toList();
    }

    @GetMapping("/{id}")
    public BusinessOperationView get(@PathVariable UUID id, @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return view(service.get(who.phone(), id));
    }

    /** The money request leaves for Safaricom here, once. */
    @PostMapping("/{id}/approve")
    public BusinessOperationView approve(@PathVariable UUID id, @Valid @RequestBody ApproveRequest request,
                                         @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return view(service.approve(who.phone(), id, request.method().name()));
    }

    @PostMapping("/{id}/decline")
    public BusinessOperationView decline(@PathVariable UUID id, @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return view(service.decline(who.phone(), id));
    }

    private BusinessOperationView view(BusinessOperation op) {
        return BusinessOperationView.of(op, service.approvalRequired(op), null);
    }
}
