package com.mganji.agent.chat;

import com.mganji.agent.exception.ChatException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * How the assistant talks to a customer. Everything it says is written to that customer's chat log, and the
 * apps (through ganji-api) read it from there. There is no outside messaging service in between.
 */
@Component
public class ChatChannel {

    public record Button(String id, String title) {
    }

    public record ListRow(String id, String title, String description) {
    }

    public record Media(byte[] bytes, String mimeType) {
    }

    private final ChatLog log;
    private final ChatMedia media;

    public ChatChannel(ChatLog log, ChatMedia media) {
        this.log = log;
        this.media = media;
    }

    public void sendText(String to, String text) {
        ChatMessage m = ChatMessage.out("TEXT");
        m.text = text;
        log.append(to, m);
    }

    public void sendButtons(String to, String body, String footer, List<Button> buttons) {
        ChatMessage m = ChatMessage.out("BUTTONS");
        m.text = body;
        m.footer = footer == null || footer.isBlank() ? null : footer;
        m.buttons = List.copyOf(buttons);
        log.append(to, m);
    }

    public void sendList(String to, String body, String buttonLabel, String sectionTitle, List<ListRow> rows) {
        ChatMessage m = ChatMessage.out("LIST");
        m.text = body;
        m.listButton = buttonLabel;
        m.listTitle = sectionTitle;
        m.rows = List.copyOf(rows);
        log.append(to, m);
    }

    /** kind is IMAGE, DOCUMENT, AUDIO or VIDEO. */
    public void sendMedia(String to, String kind, byte[] bytes, String mimeType, String fileName, String caption) {
        ChatMessage m = ChatMessage.out(kind);
        m.mediaId = media.put(to, bytes, mimeType, fileName);
        m.mimeType = mimeType;
        m.fileName = fileName;
        m.caption = caption == null || caption.isBlank() ? null : caption;
        log.append(to, m);
    }

    /** Asks the customer's app to open its contact finder for this name. The answer comes back as a shared contact. */
    public void sendContactLookup(String to, String query) {
        ChatMessage m = ChatMessage.out("CONTACT_LOOKUP");
        m.query = query;
        log.append(to, m);
    }

    /** The "typing..." line in the app. */
    public void typing(String phone, boolean on) {
        log.typing(phone, on);
    }

    /** A file this customer attached. Another customer's file id is reported as not found. */
    public Media download(String phone, String mediaId) {
        return media.get(phone, mediaId)
                .map(f -> new Media(f.bytes(), f.mimeType()))
                .orElseThrow(() -> new ChatException(404, "That file is no longer available"));
    }
}
