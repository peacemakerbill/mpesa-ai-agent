package com.darajaagentx.api.controller;

import com.darajaagentx.api.service.BusinessOperationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Where Safaricom posts the answers to business requests. This must be reachable from the internet. Every request has its
 * own secret token in the path, and an answer with the wrong token is ignored. Safaricom is always told "accepted", so a
 * wrong or repeated post never makes it retry.
 */
@RestController
@RequestMapping("/daraja/business/{id}/{token}")
public class DarajaCallbackController {

    private static final Logger log = LoggerFactory.getLogger(DarajaCallbackController.class);
    private static final Map<String, Object> ACCEPTED = Map.of("ResultCode", 0, "ResultDesc", "Accepted");

    private final BusinessOperationService service;

    public DarajaCallbackController(BusinessOperationService service) {
        this.service = service;
    }

    @PostMapping(value = "/callback", consumes = MediaType.ALL_VALUE)
    public Map<String, Object> stk(@PathVariable UUID id, @PathVariable String token, @RequestBody(required = false) Map<String, Object> body) {
        return handle(id, token, "callback", body);
    }

    @PostMapping(value = "/result", consumes = MediaType.ALL_VALUE)
    public Map<String, Object> result(@PathVariable UUID id, @PathVariable String token, @RequestBody(required = false) Map<String, Object> body) {
        return handle(id, token, "result", body);
    }

    @PostMapping(value = "/timeout", consumes = MediaType.ALL_VALUE)
    public Map<String, Object> timeout(@PathVariable UUID id, @PathVariable String token, @RequestBody(required = false) Map<String, Object> body) {
        return handle(id, token, "timeout", body);
    }

    private Map<String, Object> handle(UUID id, String token, String kind, Map<String, Object> body) {
        try {
            service.onCallback(id, token, kind, body);
        } catch (RuntimeException e) {
            log.error("Could not process the {} for business operation {}", kind, id, e);
        }
        return ACCEPTED;
    }
}
