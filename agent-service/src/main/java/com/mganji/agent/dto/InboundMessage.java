package com.mganji.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One customer message in the in-app chat. Files are not inlined: they are uploaded first and referred to by mediaId. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InboundMessage(
        String messageId,
        String from,
        String senderName,
        long timestamp,
        Kind kind,
        String text,
        String mediaId,
        String mimeType,
        String fileName,
        String caption,
        Boolean voiceNote,
        Double latitude,
        Double longitude,
        String locationLabel,
        String actionId,
        String actionTitle,
        String contactsSummary,
        String replyToMessageId,
        String reactionEmoji
) {
    public enum Kind {
        TEXT, IMAGE, AUDIO, DOCUMENT, VIDEO, STICKER, LOCATION, CONTACTS,
        BUTTON_REPLY, LIST_REPLY, REACTION, UNSUPPORTED
    }

    public static InboundMessage text(String id, String from, String text) {
        return new InboundMessage(id, from, null, 0L, Kind.TEXT, text, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }
}
