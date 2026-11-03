package com.darajaagentx.api.model;

import java.util.Locale;

/** Which Safaricom Daraja a business account talks to. Sandbox is for testing with fake money. */
public enum DarajaEnvironment {
    SANDBOX,
    PRODUCTION;

    /** Accepts sandbox, production, prod and live in any case. Returns null when the text is not one of them. */
    public static DarajaEnvironment parse(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim().toLowerCase(Locale.ROOT);
        return switch (t) {
            case "sandbox", "test", "testing" -> SANDBOX;
            case "production", "prod", "live" -> PRODUCTION;
            default -> null;
        };
    }
}
