package com.darajaagentx.api.controller;

import com.darajaagentx.api.config.DeviceAuthFilter;
import com.darajaagentx.api.config.DevicePrincipal;
import com.darajaagentx.api.exception.TooManyRequestsException;
import com.darajaagentx.api.service.ChatRelay;
import com.darajaagentx.api.service.SlidingWindowLimiter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * A customer's list of chats, kept by agent-service: start a new chat, open an old one, rename, pin and delete.
 * The number comes from the token, so each customer only ever sees and changes their own chats. Every call answers
 * with the updated list.
 */
@RestController
@RequestMapping("/api/chat/sessions")
public class ChatSessionsController {

    private static final int MAX_BODY_BYTES = 2_000;
    private static final String ID = "^[A-Za-z0-9-]{1,40}$";

    private final ChatRelay relay;
    private final SlidingWindowLimiter limiter;

    public ChatSessionsController(ChatRelay relay, SlidingWindowLimiter limiter) {
        this.relay = relay;
        this.limiter = limiter;
    }

    /** All chats: pinned first, then the most recently used. The one marked active is the one the assistant is in. */
    @GetMapping
    public ResponseEntity<byte[]> list(@RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return relay.call(HttpMethod.GET, who.phone(), "/sessions", null, null, null, null);
    }

    /** Start a new chat. The current one stays in the list. */
    @PostMapping
    public ResponseEntity<byte[]> create(@RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        checkRate(who);
        return relay.call(HttpMethod.POST, who.phone(), "/sessions", null, null, null, null);
    }

    /** Delete every chat, pinned ones included. */
    @DeleteMapping
    public ResponseEntity<byte[]> deleteAll(@RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        checkRate(who);
        return relay.call(HttpMethod.DELETE, who.phone(), "/sessions", null, null, null, null);
    }

    /** Go back to an old chat. Reload the messages afterwards. */
    @PostMapping("/{id}/open")
    public ResponseEntity<byte[]> open(@PathVariable String id, @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        if (!id.matches(ID)) {
            return notFound();
        }
        checkRate(who);
        return relay.call(HttpMethod.POST, who.phone(), "/sessions/" + id + "/open", null, null, null, null);
    }

    /** Body: {"title": "Rent and bills"}. An empty title goes back to the automatic name. */
    @PutMapping("/{id}/title")
    public ResponseEntity<byte[]> rename(@PathVariable String id, @RequestBody byte[] json,
                                         @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return write(id, "/title", json, who);
    }

    /** Body: {"pinned": true}. */
    @PutMapping("/{id}/pin")
    public ResponseEntity<byte[]> pin(@PathVariable String id, @RequestBody byte[] json,
                                      @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return write(id, "/pin", json, who);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<byte[]> delete(@PathVariable String id, @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        if (!id.matches(ID)) {
            return notFound();
        }
        checkRate(who);
        return relay.call(HttpMethod.DELETE, who.phone(), "/sessions/" + id, null, null, null, null);
    }

    private ResponseEntity<byte[]> write(String id, String what, byte[] json, DevicePrincipal who) {
        if (!id.matches(ID)) {
            return notFound();
        }
        if (json.length > MAX_BODY_BYTES) {
            return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                    .body("{\"status\":400,\"message\":\"That request is too large.\"}".getBytes(StandardCharsets.UTF_8));
        }
        checkRate(who);
        return relay.call(HttpMethod.PUT, who.phone(), "/sessions/" + id + what, null, json, MediaType.APPLICATION_JSON, null);
    }

    private void checkRate(DevicePrincipal who) {
        if (!limiter.allow("chat-sessions:" + who.phone(), 60, Duration.ofMinutes(1))) {
            throw new TooManyRequestsException("You are changing chats very quickly. Please wait a moment.");
        }
    }

    private static ResponseEntity<byte[]> notFound() {
        return ResponseEntity.status(404).contentType(MediaType.APPLICATION_JSON)
                .body("{\"status\":404,\"message\":\"That chat no longer exists.\"}".getBytes(StandardCharsets.UTF_8));
    }
}