package com.mganji.agent.service;

import com.mganji.agent.chat.ChatChannel;
import com.mganji.agent.client.TextToSpeechClient;
import com.mganji.agent.dto.InboundMessage;
import com.mganji.agent.model.Identity;
import com.mganji.agent.model.Prepared;
import com.mganji.agent.store.ConversationStore;
import com.mganji.agent.store.PhoneLock;
import com.mganji.agent.store.RateLimiter;
import com.mganji.agent.util.Phones;
import com.mganji.agent.util.PlainText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Handles one inbound message from start to finish, one message per phone number at a time.
 * Order of business: lock, mark read, rate limit, find the role, then button taps and menu choices are handled
 * without the model; everything else is turned into content and answered by the model.
 */
@Service
public class MessageProcessor {

    private static final Logger log = LoggerFactory.getLogger(MessageProcessor.class);

    static final String ERROR_REPLY =
            "Sorry, something went wrong on my side. Please try again in a moment. "
                    + "If you were making a payment, ask me for your payment status first so you are not charged twice.";
    static final String SLOW_DOWN =
            "You are sending messages very quickly. Please wait a minute and try again.";
    static final String BUSY =
            "I'm still working on your previous message. Please give me a moment.";

    private final PhoneLock lock;
    private final RateLimiter limiter;
    private final ConversationStore conversations;
    private final RoleService roles;
    private final MediaProcessor media;
    private final AgentOrchestrator orchestrator;
    private final ButtonHandler buttons;
    private final MenuService menu;
    private final ReplySender sender;
    private final ChatChannel channel;
    private final TextToSpeechClient tts;

    public MessageProcessor(PhoneLock lock, RateLimiter limiter, ConversationStore conversations, RoleService roles,
                            MediaProcessor media, AgentOrchestrator orchestrator, ButtonHandler buttons, MenuService menu,
                            ReplySender sender, ChatChannel channel, TextToSpeechClient tts) {
        this.lock = lock;
        this.limiter = limiter;
        this.conversations = conversations;
        this.roles = roles;
        this.media = media;
        this.orchestrator = orchestrator;
        this.buttons = buttons;
        this.menu = menu;
        this.sender = sender;
        this.channel = channel;
        this.tts = tts;
    }

    public void process(InboundMessage message) {
        String phone = Phones.normalize(message.from());
        Optional<String> token = lock.acquire(phone, Duration.ofSeconds(60));
        if (token.isEmpty()) {
            log.warn("Gave up waiting for the lock of {}", Phones.mask(phone));
            safeSend(phone, BUSY);
            return;
        }
        try {
            handle(phone, message);
        } catch (RuntimeException e) {
            log.error("Failed to handle message {} from {}", message.messageId(), Phones.mask(phone), e);
            safeSend(phone, ERROR_REPLY);
        } finally {
            channel.typing(phone, false);
            lock.release(phone, token.get());
        }
    }

    void handle(String phone, InboundMessage message) {
        channel.typing(phone, message.kind() != InboundMessage.Kind.REACTION);

        if (!limiter.allow(phone)) {
            sender.text(phone, SLOW_DOWN);
            return;
        }
        Identity who = roles.identify(phone);

        InboundMessage effective = message;
        switch (message.kind()) {
            case REACTION -> {
                return;
            }
            case BUTTON_REPLY, LIST_REPLY -> {
                if (ButtonHandler.handles(message.actionId())) {
                    buttons.handle(phone, message.actionId());
                    return;
                }
                // A menu row or a template quick reply: treat it as if the person had typed it
                String asText = menu.requestFor(message.actionId()).orElse(message.actionTitle());
                if (asText == null || asText.isBlank()) {
                    return;
                }
                effective = InboundMessage.text(message.messageId(), message.from(), asText);
            }
            default -> {
            }
        }

        if (effective.kind() == InboundMessage.Kind.TEXT && menu.isTrigger(effective.text())) {
            sender.list(phone, menu.welcomeText(who), "Choose", "How can I help?", menu.rowsFor(who.role()));
            return;
        }

        Prepared prepared = media.prepare(effective);
        if (prepared.ignore()) {
            return;
        }
        if (prepared.directReply() != null) {
            sender.text(phone, prepared.directReply());
            return;
        }

        ToolContext ctx = new ToolContext(who, message.messageId(), sourceOf(effective));
        String answer = orchestrator.respond(ctx, prepared);
        sender.text(phone, answer);
        ctx.deferred().forEach(action -> action.accept(sender));

        conversations.append(phone, "user", prepared.historyText());
        conversations.append(phone, "assistant", answer);

        if (prepared.voiceInput() && ctx.deferred().isEmpty()) {
            speak(phone, answer);
        }
    }

    /** How the details in this message reached us. Voice and pictures are the easiest to get wrong. */
    static String sourceOf(InboundMessage m) {
        return switch (m.kind()) {
            case AUDIO -> "VOICE";
            case IMAGE, STICKER -> "IMAGE";
            case DOCUMENT -> "DOCUMENT";
            case CONTACTS -> "CONTACT";
            default -> "TEXT";
        };
    }

    /** Optional spoken copy of a short answer. Failure is silently ignored, the text already went out. */
    private void speak(String phone, String answer) {
        if (!tts.enabled()) {
            return;
        }
        String plain = PlainText.strip(answer);
        if (plain.isEmpty() || plain.length() > 500) {
            return;
        }
        try {
            tts.speak(plain).ifPresent(audio -> sender.audio(phone, audio));
        } catch (RuntimeException e) {
            log.warn("Could not send the spoken reply: {}", e.getMessage());
        }
    }

    private void safeSend(String phone, String text) {
        try {
            sender.text(phone, text);
        } catch (RuntimeException e) {
            log.error("Could not even send an error message to {}: {}", Phones.mask(phone), e.getMessage());
        }
    }
}
