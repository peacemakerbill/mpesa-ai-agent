package com.mganji.agent.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.mganji.agent.dto.InboundMessage;

import java.util.List;

/**
 * One line of a customer's chat, as stored and as sent to the apps. {@code dir} is "in" for what the customer
 * wrote and "out" for what the assistant said. Only the fields that apply to the kind are filled.
 *
 * Kinds: TEXT, BUTTONS (text with up to three buttons), LIST (text with a menu of rows), IMAGE, DOCUMENT, AUDIO,
 * VIDEO, LOCATION, CONTACTS, CONTACT_LOOKUP (asks the app to show its contact finder), BUTTON_REPLY, LIST_REPLY.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessage {

    public long id;
    public String dir;
    public String kind;
    /** Epoch milliseconds. */
    public long at;

    public String text;
    public String footer;
    public List<ChatChannel.Button> buttons;
    public String listButton;
    public String listTitle;
    public List<ChatChannel.ListRow> rows;

    public String mediaId;
    public String mimeType;
    public String fileName;
    public String caption;
    public Boolean voiceNote;

    public Double latitude;
    public Double longitude;
    public String locationLabel;
    public String contactsSummary;
    /** For CONTACT_LOOKUP: the name the customer asked for. */
    public String query;
    public String actionId;
    public String actionTitle;

    public ChatMessage() {
    }

    public static ChatMessage out(String kind) {
        ChatMessage m = new ChatMessage();
        m.dir = "out";
        m.kind = kind;
        return m;
    }

    /** What the customer sent, in the form the chat screens draw. */
    public static ChatMessage fromInbound(InboundMessage in) {
        ChatMessage m = new ChatMessage();
        m.dir = "in";
        m.kind = in.kind().name();
        m.text = in.text();
        m.mediaId = in.mediaId();
        m.mimeType = in.mimeType();
        m.fileName = in.fileName();
        m.caption = in.caption();
        m.voiceNote = in.voiceNote();
        m.latitude = in.latitude();
        m.longitude = in.longitude();
        m.locationLabel = in.locationLabel();
        m.contactsSummary = in.contactsSummary();
        m.actionId = in.actionId();
        m.actionTitle = in.actionTitle();
        return m;
    }
}
