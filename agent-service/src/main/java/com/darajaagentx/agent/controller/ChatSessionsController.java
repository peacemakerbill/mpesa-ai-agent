package com.darajaagentx.agent.controller;

import com.darajaagentx.agent.chat.ChatSessions;
import com.darajaagentx.agent.util.Phones;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.function.Function;

/**
 * The customer's list of chats: start a new one, open an old one, rename, pin and delete. Like the rest of the in-app
 * chat, only agentx-api calls this, with the signed-in customer's number in the path. Every call answers with the
 * updated list, so an app can redraw from the answer alone.
 */
@RestController
@RequestMapping("/internal/chat/{phone}/sessions")
public class ChatSessionsController {

    public record TitleRequest(String title) {
    }

    public record PinRequest(Boolean pinned) {
    }

    private final ChatSessions sessions;

    public ChatSessionsController(ChatSessions sessions) {
        this.sessions = sessions;
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable String phone) {
        return run(phone, sessions::list);
    }

    /** Starts a new chat. The current one is kept in the list. */
    @PostMapping
    public ResponseEntity<?> create(@PathVariable String phone) {
        return run(phone, sessions::create);
    }

    @DeleteMapping
    public ResponseEntity<?> deleteAll(@PathVariable String phone) {
        return run(phone, sessions::deleteAll);
    }

    /** Makes this chat the current one. The app then reloads the messages. */
    @PostMapping("/{id}/open")
    public ResponseEntity<?> open(@PathVariable String phone, @PathVariable String id) {
        return run(phone, who -> sessions.open(who, id));
    }

    @PutMapping("/{id}/title")
    public ResponseEntity<?> rename(@PathVariable String phone, @PathVariable String id, @RequestBody TitleRequest request) {
        return run(phone, who -> sessions.rename(who, id, request == null ? null : request.title()));
    }

    @PutMapping("/{id}/pin")
    public ResponseEntity<?> pin(@PathVariable String phone, @PathVariable String id, @RequestBody PinRequest request) {
        if (request == null || request.pinned() == null) {
            return problem(HttpStatus.BAD_REQUEST, "Say whether to pin or unpin the chat.");
        }
        return run(phone, who -> sessions.pin(who, id, request.pinned()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String phone, @PathVariable String id) {
        return run(phone, who -> sessions.delete(who, id));
    }

    private ResponseEntity<?> run(String phone, Function<String, ChatSessions.Listing> action) {
        String who = Phones.normalize(phone);
        if (who.isEmpty()) {
            return problem(HttpStatus.BAD_REQUEST, "The phone number is not valid.");
        }
        try {
            return ResponseEntity.ok(action.apply(who));
        } catch (ChatSessions.Failure e) {
            return problem(HttpStatus.valueOf(e.status()), e.getMessage());
        }
    }

    private static ResponseEntity<?> problem(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "message", message));
    }
}