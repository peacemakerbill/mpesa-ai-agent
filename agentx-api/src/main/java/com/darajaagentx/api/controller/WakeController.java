package com.darajaagentx.api.controller;

import com.darajaagentx.api.dto.WakeResponse;
import com.darajaagentx.api.service.AgentWake;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Open to everyone, needs no sign-in, and shows nothing private. The app calls it when it opens, before the customer
 * signs in, to start whatever is asleep and to know when everything is ready.
 */
@RestController
@RequestMapping("/api")
public class WakeController {

    private final AgentWake wake;

    public WakeController(AgentWake wake) {
        this.wake = wake;
    }

    @GetMapping("/wake")
    public ResponseEntity<WakeResponse> wake() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(WakeResponse.of(wake.awake()));
    }
}
