package com.mganji.agent.model;

import java.util.List;
import java.util.Map;

/**
 * What an inbound message became: content blocks for the model, or a ready made reply when the message
 * cannot be understood (or does not need a reply at all).
 *
 * @param blocks      Claude content blocks (text, image, document)
 * @param historyText a short text form that is stored in the chat history instead of the raw file
 * @param voiceInput  true when the customer spoke, so a spoken reply can be offered
 * @param directReply answer sent as is without calling the model
 * @param ignore      nothing to answer (a reaction or a sticker)
 */
public record Prepared(List<Map<String, Object>> blocks, String historyText, boolean voiceInput,
                       String directReply, boolean ignore) {

    public static Prepared of(List<Map<String, Object>> blocks, String historyText, boolean voiceInput) {
        return new Prepared(blocks, historyText, voiceInput, null, false);
    }

    public static Prepared reply(String text) {
        return new Prepared(List.of(), text, false, text, false);
    }

    public static Prepared skip() {
        return new Prepared(List.of(), "", false, null, true);
    }
}
