package com.mganji.agent.controller;

import com.mganji.agent.chat.ChatLog;
import com.mganji.agent.chat.ChatMedia;
import com.mganji.agent.config.AgentProperties;
import com.mganji.agent.dto.ChatPage;
import com.mganji.agent.dto.ChatSendRequest;
import com.mganji.agent.dto.InboundMessage;
import com.mganji.agent.service.InboundIntake;
import com.mganji.agent.store.ConversationStore;
import com.mganji.agent.util.Phones;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The in-app chat. Only ganji-api calls this (the internal key protects it) and it puts the signed-in
 * customer's number in the path, so the apps never choose whose chat they read.
 */
@RestController
@RequestMapping("/internal/chat/{phone}")
public class ChatController {

    private static final Set<InboundMessage.Kind> CUSTOMER_KINDS = Set.of(
            InboundMessage.Kind.TEXT, InboundMessage.Kind.IMAGE, InboundMessage.Kind.AUDIO, InboundMessage.Kind.DOCUMENT,
            InboundMessage.Kind.VIDEO, InboundMessage.Kind.LOCATION, InboundMessage.Kind.CONTACTS,
            InboundMessage.Kind.BUTTON_REPLY, InboundMessage.Kind.LIST_REPLY);
    private static final Set<InboundMessage.Kind> MEDIA_KINDS = Set.of(
            InboundMessage.Kind.IMAGE, InboundMessage.Kind.AUDIO, InboundMessage.Kind.DOCUMENT, InboundMessage.Kind.VIDEO);

    private final InboundIntake intake;
    private final ChatLog chatLog;
    private final ChatMedia media;
    private final ConversationStore conversations;
    private final AgentProperties props;

    public ChatController(InboundIntake intake, ChatLog chatLog, ChatMedia media, ConversationStore conversations, AgentProperties props) {
        this.intake = intake;
        this.chatLog = chatLog;
        this.media = media;
        this.conversations = conversations;
        this.props = props;
    }

    @PostMapping("/send")
    public ResponseEntity<Map<String, String>> send(@PathVariable String phone, @RequestBody ChatSendRequest r) {
        String who = Phones.normalize(phone);
        if (who.isEmpty() || r.kind() == null || !CUSTOMER_KINDS.contains(r.kind())) {
            return problem(HttpStatus.BAD_REQUEST, "That kind of message cannot be sent.");
        }
        if (r.kind() == InboundMessage.Kind.TEXT && (r.text() == null || r.text().isBlank())) {
            return problem(HttpStatus.BAD_REQUEST, "The message is empty.");
        }
        if (r.text() != null && r.text().length() > props.maxTextChars()) {
            return problem(HttpStatus.BAD_REQUEST, "That message is too long.");
        }
        String mimeType = r.mimeType();
        if (MEDIA_KINDS.contains(r.kind())) {
            var found = media.get(who, r.mediaId());
            if (found.isEmpty()) {
                return problem(HttpStatus.BAD_REQUEST, "Upload the file first, then send the message again.");
            }
            mimeType = found.get().mimeType();
        }
        if (r.kind() == InboundMessage.Kind.LOCATION && (r.latitude() == null || r.longitude() == null)) {
            return problem(HttpStatus.BAD_REQUEST, "A location needs a latitude and a longitude.");
        }
        if (r.kind() == InboundMessage.Kind.CONTACTS && (r.contactsSummary() == null || r.contactsSummary().isBlank())) {
            return problem(HttpStatus.BAD_REQUEST, "A shared contact needs a name and a number.");
        }
        if ((r.kind() == InboundMessage.Kind.BUTTON_REPLY || r.kind() == InboundMessage.Kind.LIST_REPLY)
                && (r.actionId() == null || r.actionId().isBlank())) {
            return problem(HttpStatus.BAD_REQUEST, "A button or menu choice needs its id.");
        }
        String id = r.clientMessageId() == null || r.clientMessageId().isBlank()
                ? "app-" + UUID.randomUUID() : who + ":" + r.clientMessageId().strip();
        InboundMessage message = new InboundMessage(id, who, r.senderName(), Instant.now().getEpochSecond(), r.kind(), r.text(),
                r.mediaId(), mimeType, r.fileName(), r.caption(), r.voiceNote(), r.latitude(), r.longitude(), r.locationLabel(),
                r.actionId(), r.actionTitle(), r.contactsSummary(), null, null);
        return switch (intake.accept(message)) {
            case BUSY -> problem(HttpStatus.SERVICE_UNAVAILABLE, "The assistant is busy. Please send that again in a moment.");
            case ACCEPTED, DUPLICATE -> ResponseEntity.accepted().body(Map.of("messageId", id));
        };
    }

    @GetMapping("/messages")
    public ChatPage messages(@PathVariable String phone, @RequestParam(defaultValue = "0") long after,
                             @RequestParam(defaultValue = "100") int limit) {
        String who = Phones.normalize(phone);
        int capped = Math.max(1, Math.min(limit, 200));
        return new ChatPage(chatLog.after(who, Math.max(0, after), capped), chatLog.isTyping(who), chatLog.lastId(who));
    }

    @DeleteMapping("/messages")
    public ResponseEntity<Void> clear(@PathVariable String phone) {
        String who = Phones.normalize(phone);
        chatLog.clear(who);
        conversations.clear(who);
        return ResponseEntity.noContent().build();
    }

    /** The app uploads a file first, then sends a message that refers to the id it gets back. */
    @PostMapping("/media")
    public ResponseEntity<?> upload(@PathVariable String phone, @RequestBody byte[] bytes,
                                    @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
                                    @RequestHeader(value = "X-File-Name", required = false) String fileName) {
        String who = Phones.normalize(phone);
        if (bytes == null || bytes.length == 0) {
            return problem(HttpStatus.BAD_REQUEST, "The file is empty.");
        }
        if (bytes.length > props.maxUploadBytes()) {
            return problem(HttpStatus.CONTENT_TOO_LARGE, "That file is larger than " + (props.maxUploadBytes() / (1024 * 1024)) + " MB.");
        }
        String type = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType.split(";")[0].trim();
        String id = media.put(who, bytes, type, fileName);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("mediaId", id, "mimeType", type, "size", bytes.length));
    }

    @GetMapping("/media/{id}")
    public ResponseEntity<byte[]> download(@PathVariable String phone, @PathVariable String id) {
        return media.get(Phones.normalize(phone), id).map(f -> {
            ResponseEntity.BodyBuilder ok = ResponseEntity.ok().contentType(parse(f.mimeType()));
            if (f.fileName() != null && !f.fileName().isBlank()) {
                ok.header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(f.fileName(), StandardCharsets.UTF_8).build().toString());
            }
            return ok.body(f.bytes());
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static MediaType parse(String type) {
        try {
            return MediaType.parseMediaType(type);
        } catch (RuntimeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static <T> ResponseEntity<T> problem(HttpStatus status, String message) {
        @SuppressWarnings("unchecked")
        ResponseEntity<T> r = (ResponseEntity<T>) ResponseEntity.status(status).body(Map.of("status", status.value(), "message", message));
        return r;
    }
}