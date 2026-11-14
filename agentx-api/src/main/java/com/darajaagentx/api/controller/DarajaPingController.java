package com.darajaagentx.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Lets you check from a browser that the address you gave for Safaricom's answers (AGENTX_STK_CALLBACK_URL or the public address)
 * really leads to this service: open ADDRESS/daraja/ping. It shows nothing private.
 */
@RestController
@RequestMapping("/daraja/ping")
public class DarajaPingController {

    @GetMapping
    public Map<String, String> ping() {
        return Map.of("status", "ok", "service", "agentx-api");
    }
}
