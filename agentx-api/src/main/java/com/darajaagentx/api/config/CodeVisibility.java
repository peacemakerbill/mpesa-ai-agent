package com.darajaagentx.api.config;

import java.util.Locale;

/**
 * Whether a sign-in code is also shown on the screen of the app that asked for it (CODE_SHOW_IN_APP).
 * It is a test aid: whoever can reach the address and knows a phone number can sign in as that number.
 *
 *  - auto: shown in development and staging, never in production. The default.
 *  - on:   shown in development and staging. In production it is refused at startup, and never shown.
 *  - off:  never shown. Use this once codes are sent by SMS.
 */
public enum CodeVisibility {
    AUTO, ON, OFF;

    /** Reads true, on, yes, 1, false, off, no, 0 and auto in any case. Blank or unknown means auto. */
    public static CodeVisibility parse(String text) {
        if (text == null || text.isBlank()) {
            return AUTO;
        }
        return switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "true", "on", "yes", "1", "show" -> ON;
            case "false", "off", "no", "0", "hide" -> OFF;
            default -> AUTO;
        };
    }

    /** The production system never shows a code, whatever is set. */
    public boolean shownIn(AppEnvironment env) {
        return !env.isProduction() && this != OFF;
    }
}
