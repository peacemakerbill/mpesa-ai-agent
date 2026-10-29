package com.mpesaagent.companion.controller;

import com.mpesaagent.companion.config.DeviceAuthFilter;
import com.mpesaagent.companion.config.DevicePrincipal;
import com.mpesaagent.companion.exception.TooManyRequestsException;
import com.mpesaagent.companion.service.ChatRelay;
import com.mpesaagent.companion.service.SlidingWindowLimiter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * The chat the apps use. A signed-in customer reads and writes only their own chat: the number comes from the
 * token, never from the request. The conversation itself is kept by agent-service.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final int MAX_MESSAGE_BYTES = 200_000;

    private final ChatRelay relay;
    private final SlidingWindowLimiter limiter;

    public ChatController(ChatRelay relay, SlidingWindowLimiter limiter) {
        this.relay = relay;
        this.limiter = limiter;
    }

    /** Send one message (text, a button or menu choice, a location, a contact, or a file already uploaded). */
    @PostMapping("/messages")
    public ResponseEntity<byte[]> send(@RequestBody byte[] json, @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        if (!limiter.allow("chat:" + who.phone(), 60, Duration.ofMinutes(1))) {
            throw new TooManyRequestsException("You are sending messages very quickly. Please wait a moment.");
        }
        if (json.length > MAX_MESSAGE_BYTES) {
            return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                    .body("{\"status\":400,\"message\":\"That message is too large.\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return relay.call(HttpMethod.POST, who.phone(), "/send", null, json, MediaType.APPLICATION_JSON, null);
    }

    /** Everything newer than message number {@code after}. Poll this every second or two while the chat is open. */
    @GetMapping("/messages")
    public ResponseEntity<byte[]> messages(@RequestParam(defaultValue = "0") long after, @RequestParam(defaultValue = "100") int limit,
                                           @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return relay.call(HttpMethod.GET, who.phone(), "/messages", "after=" + Math.max(0, after) + "&limit=" + Math.max(1, Math.min(limit, 200)),
                null, null, null);
    }

    /** Start the chat over. */
    @DeleteMapping("/messages")
    public ResponseEntity<byte[]> clear(@RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return relay.call(HttpMethod.DELETE, who.phone(), "/messages", null, null, null, null);
    }

    /** Upload a file first (photo, voice note, document, video), then send a message that names the returned id. */
    @PostMapping("/media")
    public ResponseEntity<byte[]> upload(@RequestBody byte[] bytes,
                                         @RequestHeader(value = "Content-Type", required = false) String contentType,
                                         @RequestHeader(value = "X-File-Name", required = false) String fileName,
                                         @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        if (!limiter.allow("chat-upload:" + who.phone(), 30, Duration.ofMinutes(10))) {
            throw new TooManyRequestsException("You are uploading files very quickly. Please wait a few minutes.");
        }
        MediaType type;
        try {
            type = contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType);
        } catch (RuntimeException e) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return relay.call(HttpMethod.POST, who.phone(), "/media", null, bytes, type, fileName);
    }

    /** A file in this customer's chat, from either side. */
    @GetMapping("/media/{id}")
    public ResponseEntity<byte[]> download(@PathVariable String id, @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        if (!id.matches("^[0-9a-fA-F-]{36}$")) {
            return ResponseEntity.notFound().build();
        }
        return relay.call(HttpMethod.GET, who.phone(), "/media/" + id, null, null, null, null);
    }
}
