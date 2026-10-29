package com.mpesaagent.agent.controller;

import com.mpesaagent.agent.dto.InboundMessage;
import com.mpesaagent.agent.service.InboundIntake;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Puts a message into a customer's chat as if they had typed it. companion-api uses the chat endpoints below for
 * real customers; this one takes the full message shape and is handy for tests. We accept fast (202) and answer
 * on our own threads.
 */
@RestController
@RequestMapping("/internal")
public class InboundController {

    private final InboundIntake intake;

    public InboundController(InboundIntake intake) {
        this.intake = intake;
    }

    @PostMapping("/inbound")
    public ResponseEntity<Void> inbound(@RequestBody InboundMessage message) {
        if (message.messageId() == null || message.messageId().isBlank()
                || message.from() == null || message.from().isBlank() || message.kind() == null) {
            return ResponseEntity.badRequest().build();
        }
        return switch (intake.accept(message)) {
            case BUSY -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            case ACCEPTED, DUPLICATE -> ResponseEntity.accepted().build();
        };
    }
}
