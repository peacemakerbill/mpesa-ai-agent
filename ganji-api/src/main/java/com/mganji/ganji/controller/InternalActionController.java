package com.mganji.ganji.controller;

import com.mganji.ganji.dto.ActionResponse;
import com.mganji.ganji.dto.CreateActionRequest;
import com.mganji.ganji.dto.LinkCodeResponse;
import com.mganji.ganji.dto.PhoneRequest;
import com.mganji.ganji.dto.ReceiptRequest;
import com.mganji.ganji.service.ActionService;
import com.mganji.ganji.service.LinkService;
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

/** Called only by agent-service (X-Internal-Key). */
@RestController
@RequestMapping("/internal")
public class InternalActionController {

    private final ActionService actions;
    private final LinkService links;

    public InternalActionController(ActionService actions, LinkService links) {
        this.actions = actions;
        this.links = links;
    }

    @PostMapping("/actions")
    public ActionResponse create(@Valid @RequestBody CreateActionRequest request) {
        return ActionResponse.from(actions.create(request));
    }

    @GetMapping("/actions")
    public List<ActionResponse> list(@RequestParam String phone, @RequestParam(defaultValue = "10") int limit) {
        return actions.list(phone, limit).stream().map(ActionResponse::from).toList();
    }

    @GetMapping("/actions/{id}")
    public ActionResponse get(@PathVariable UUID id, @RequestParam String phone) {
        return ActionResponse.from(actions.get(id, phone));
    }

    @PostMapping("/actions/{id}/cancel")
    public ActionResponse cancel(@PathVariable UUID id, @Valid @RequestBody PhoneRequest request) {
        return ActionResponse.from(actions.cancel(id, request.phone()));
    }

    @PostMapping("/actions/{id}/receipt")
    public ActionResponse receipt(@PathVariable UUID id, @Valid @RequestBody ReceiptRequest request) {
        return ActionResponse.from(actions.attachReceipt(id, request.phone(), request.receipt()));
    }

    @PostMapping("/link-codes")
    public LinkCodeResponse linkCode(@Valid @RequestBody PhoneRequest request) {
        return links.issue(request.phone());
    }
}
