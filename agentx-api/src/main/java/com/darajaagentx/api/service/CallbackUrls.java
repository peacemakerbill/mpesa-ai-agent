package com.darajaagentx.api.service;

import java.util.UUID;

/**
 * Builds the address Safaricom is told to call for a request. A configured value is either an address (the path and the
 * request's token are added) or a template that has {@code {id}} and {@code {token}} in it (used as written).
 */
public final class CallbackUrls {

    private static final UUID SAMPLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private CallbackUrls() {
    }

    /** True when the value is a template, so it already says where the id and the token go. */
    public static boolean isTemplate(String configured) {
        return configured != null && (configured.contains("{id}") || configured.contains("{token}"));
    }

    /**
     * @param configured   the address or template from the settings, may be blank
     * @param fallbackBase the address to use when nothing is configured, with no closing slash
     * @param kind         "result", "timeout" or "callback"
     */
    public static String resolve(String configured, String fallbackBase, UUID id, String token, String kind) {
        String value = configured == null ? "" : configured.strip();
        if (value.isEmpty()) {
            return path(fallbackBase, id, token, kind);
        }
        if (isTemplate(value)) {
            return value.replace("{id}", id.toString()).replace("{token}", token).replace("{kind}", kind);
        }
        return path(StkCallback.normalize(value), id, token, kind);
    }

    /** What is wrong with a configured value, or null when it looks usable. Blank is fine: the public address is used. */
    public static String problem(String configured) {
        String value = configured == null ? "" : configured.strip();
        if (value.isEmpty()) {
            return null;
        }
        if (isTemplate(value) && !(value.contains("{id}") && value.contains("{token}"))) {
            return "a template needs both {id} and {token}";
        }
        return StkCallback.problem(resolve(value, "", SAMPLE_ID, "token", "result"));
    }

    private static String path(String base, UUID id, String token, String kind) {
        return base + "/daraja/business/" + id + "/" + token + "/" + kind;
    }
}
