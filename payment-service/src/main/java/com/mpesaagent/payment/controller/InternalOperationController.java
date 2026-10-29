package com.mpesaagent.payment.controller;

import com.mpesaagent.payment.dto.ActorRequest;
import com.mpesaagent.payment.dto.CreateOperationRequest;
import com.mpesaagent.payment.dto.OperationResponse;
import com.mpesaagent.payment.dto.RejectRequest;
import com.mpesaagent.payment.service.OperationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Refunds, payouts, reversals, balance and transaction lookups. Called only by our own services. */
@RestController
@RequestMapping("/internal/operations")
public class InternalOperationController {

    private final OperationService operations;

    public InternalOperationController(OperationService operations) {
        this.operations = operations;
    }

    @PostMapping
    public OperationResponse create(@Valid @RequestBody CreateOperationRequest request) {
        return OperationResponse.from(operations.request(request));
    }

    @GetMapping("/{id}")
    public OperationResponse get(@PathVariable UUID id, @RequestParam String actorPhone) {
        return OperationResponse.from(operations.get(id, actorPhone));
    }

    /** The caller's own recent requests. */
    @GetMapping
    public List<OperationResponse> mine(@RequestParam String actorPhone) {
        return operations.recent(actorPhone).stream().map(OperationResponse::from).toList();
    }

    /** Requests waiting for a decision. Approvers only. */
    @GetMapping("/pending")
    public List<OperationResponse> pending(@RequestParam String actorPhone) {
        return operations.pendingApprovals(actorPhone).stream().map(OperationResponse::from).toList();
    }

    @PostMapping("/{id}/approve")
    public OperationResponse approve(@PathVariable UUID id, @Valid @RequestBody ActorRequest request) {
        return OperationResponse.from(operations.approve(id, request.actorPhone()));
    }

    @PostMapping("/{id}/reject")
    public OperationResponse reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest request) {
        return OperationResponse.from(operations.reject(id, request.actorPhone(), request.reason()));
    }
}
