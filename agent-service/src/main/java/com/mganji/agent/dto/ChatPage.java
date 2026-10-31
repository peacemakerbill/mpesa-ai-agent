package com.mganji.agent.dto;

import com.mganji.agent.chat.ChatMessage;

import java.util.List;

/** Messages newer than the one the app already has, plus whether the assistant is working on an answer. */
public record ChatPage(List<ChatMessage> messages, boolean typing, long lastId) {
}
