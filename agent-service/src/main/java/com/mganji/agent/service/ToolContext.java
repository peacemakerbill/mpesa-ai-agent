package com.mganji.agent.service;

import com.mganji.agent.model.Identity;
import com.mganji.agent.model.Role;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Who is asking and what to send after the model's answer. Tools that need to send a button message or a file
 * do not send it at once, they queue it here, so the customer reads the assistant's sentence first and then
 * sees the buttons.
 */
public final class ToolContext {

    private final Identity identity;
    private final String messageId;
    private final String source;
    private final List<Consumer<ReplySender>> deferred = new ArrayList<>();

    public ToolContext(Identity identity, String messageId) {
        this(identity, messageId, "TEXT");
    }

    /**
     * @param source how the person's current message arrived: TEXT, VOICE, IMAGE, DOCUMENT or CONTACT.
     *               Details that came from speech or pictures are shown with an extra warning in the app.
     */
    public ToolContext(Identity identity, String messageId, String source) {
        this.identity = identity;
        this.messageId = messageId;
        this.source = source == null ? "TEXT" : source;
    }

    public String source() {
        return source;
    }

    public Identity identity() {
        return identity;
    }

    public String phone() {
        return identity.phone();
    }

    public Role role() {
        return identity.role();
    }

    public String messageId() {
        return messageId;
    }

    public void defer(Consumer<ReplySender> action) {
        deferred.add(action);
    }

    public List<Consumer<ReplySender>> deferred() {
        return List.copyOf(deferred);
    }
}
