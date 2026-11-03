package com.darajaagentx.api.controller;

import com.darajaagentx.api.dto.BusinessAccountView;
import com.darajaagentx.api.dto.BusinessOperationView;
import com.darajaagentx.api.dto.PrepareOperationRequest;
import com.darajaagentx.api.exception.InvalidActionRequestException;
import com.darajaagentx.api.model.BusinessOperation;
import com.darajaagentx.api.service.BusinessAccountService;
import com.darajaagentx.api.service.BusinessOperationService;
import com.darajaagentx.api.util.Phones;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * For agent-service only (the X-Internal-Key filter protects /internal/**). The assistant can see the names and abilities
 * of a customer's business accounts and prepare requests on them. It never sees a secret, and the phone number is
 * the one the customer is chatting from.
 */
@RestController
@RequestMapping("/internal/business")
public class InternalBusinessController {

    private final BusinessAccountService accounts;
    private final BusinessOperationService service;

    public InternalBusinessController(BusinessAccountService accounts, BusinessOperationService service) {
        this.accounts = accounts;
        this.service = service;
    }

    @GetMapping("/accounts")
    public List<BusinessAccountView> accounts(@RequestParam String phone) {
        return accounts.list(Phones.normalize(phone));
    }

    @PostMapping("/operations")
    public ResponseEntity<BusinessOperationView> prepare(@RequestBody PrepareOperationRequest request) {
        if (request == null || request.type() == null) {
            throw new InvalidActionRequestException("Say what to do.");
        }
        String owner = Phones.normalize(request.phone());
        BusinessOperationService.Prepared prepared = service.prepare(owner, request.account(), request.type(),
                request.params(), request.source(), request.idempotencyKey());
        BusinessOperation op = prepared.operation();
        return ResponseEntity.status(HttpStatus.CREATED).body(BusinessOperationView.of(op, service.approvalRequired(op), prepared.qrCode()));
    }

    @GetMapping("/operations")
    public List<BusinessOperationView> list(@RequestParam String phone, @RequestParam(defaultValue = "5") int limit) {
        return service.list(Phones.normalize(phone)).stream().limit(Math.max(1, Math.min(limit, 10)))
                .map(o -> BusinessOperationView.of(o, service.approvalRequired(o), null)).toList();
    }

    @GetMapping("/operations/{id}")
    public BusinessOperationView get(@PathVariable UUID id, @RequestParam String phone) {
        BusinessOperation op = service.get(Phones.normalize(phone), id);
        return BusinessOperationView.of(op, service.approvalRequired(op), null);
    }

    /** The customer changed their mind in the chat. Same as declining in the app. */
    @PostMapping("/operations/{id}/cancel")
    public BusinessOperationView cancel(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        BusinessOperation op = service.decline(Phones.normalize(body.get("phone")), id);
        return BusinessOperationView.of(op, service.approvalRequired(op), null);
    }
}
