package com.darajaagentx.api.config;

import java.util.Locale;

/**
 * Which copy of the system this is: a developer's machine, the shared test copy, or the live one customers use.
 * Set with APP_ENV (agentx.env). This is not the same thing as a business account's Daraja sandbox or production,
 * which is chosen per account. It decides how strict the startup checks are and whether live Daraja may be called.
 */
public enum AppEnvironment {
    DEVELOPMENT("development", "[DEV] "),
    STAGING("staging", "[STAGING] "),
    PRODUCTION("production", "");

    private final String label;
    private final String mailTag;

    AppEnvironment(String label, String mailTag) {
        this.label = label;
        this.mailTag = mailTag;
    }

    /** The lower case name, as written in APP_ENV and shown in the console and the app. */
    public String label() {
        return label;
    }

    /** Put in front of email subjects so a test email is never taken for a real one. Empty in production. */
    public String mailTag() {
        return mailTag;
    }

    public boolean isProduction() {
        return this == PRODUCTION;
    }

    /**
     * Accepts development, dev, local, staging, stage, uat, qa, test, production, prod and live in any case.
     * Blank means development. Returns null when the text is not one of them, so a typo is caught at startup
     * instead of quietly running as something else.
     */
    public static AppEnvironment parse(String text) {
        if (text == null || text.isBlank()) {
            return DEVELOPMENT;
        }
        return switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "development", "dev", "local" -> DEVELOPMENT;
            case "staging", "stage", "uat", "qa", "test" -> STAGING;
            case "production", "prod", "live" -> PRODUCTION;
            default -> null;
        };
    }
}
