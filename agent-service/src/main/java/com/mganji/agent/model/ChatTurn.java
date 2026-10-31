package com.mganji.agent.model;

/** One line of remembered conversation. Only text is kept, never files or voice. */
public record ChatTurn(String role, String text) {
}
