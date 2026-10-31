package com.mganji.agent.service;

import com.mganji.agent.chat.ChatChannel;
import com.mganji.agent.chat.ChatChannel.Button;
import com.mganji.agent.chat.ChatChannel.ListRow;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Everything that goes out to a person, in the customer's chat. A reply to something they just wrote and a
 * notification that arrives later (a payment finished, an approval is waiting) use the same chat, so the two
 * kinds of sending differ only in name. The apps draw the Markdown style text.
 */
@Component
public class ReplySender {

    private final ChatChannel channel;

    public ReplySender(ChatChannel channel) {
        this.channel = channel;
    }

    // ------------------------------------------------------------ in a conversation

    public void text(String phone, String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return;
        }
        channel.sendText(phone, markdown.strip());
    }

    public void buttons(String phone, String body, String footer, List<Button> buttons) {
        channel.sendButtons(phone, body, footer, buttons);
    }

    public void list(String phone, String body, String buttonLabel, String sectionTitle, List<ListRow> rows) {
        channel.sendList(phone, body, buttonLabel, sectionTitle, rows);
    }

    public void document(String phone, byte[] bytes, String mimeType, String fileName, String caption) {
        channel.sendMedia(phone, "DOCUMENT", bytes, mimeType, fileName, caption);
    }

    public void image(String phone, byte[] bytes, String mimeType, String caption) {
        channel.sendMedia(phone, "IMAGE", bytes, mimeType, "image", caption);
    }

    public void audio(String phone, byte[] bytes) {
        channel.sendMedia(phone, "AUDIO", bytes, "audio/mpeg", "reply.mp3", null);
    }

    /** Opens the contact finder in the customer's app for this name. */
    public void contactLookup(String phone, String query) {
        channel.sendContactLookup(phone, query);
    }

    // ------------------------------------------------------------ notifications

    public void notifyText(String phone, String markdown) {
        text(phone, markdown);
    }

    /** Notification with optional buttons. */
    public void notifyWithButtons(String phone, String markdown, String footer, List<Button> buttons) {
        if (buttons == null || buttons.isEmpty()) {
            text(phone, markdown);
        } else {
            buttons(phone, markdown, footer, buttons);
        }
    }
}
