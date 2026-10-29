package com.mpesaagent.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * What an app sends for one chat message. The sender's number is never part of it: companion-api adds the number
 * from the login token. {@code clientMessageId} lets the app retry safely, a repeat is ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatSendRequest(
        String clientMessageId,
        InboundMessage.Kind kind,
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
        String senderName
) {
}
