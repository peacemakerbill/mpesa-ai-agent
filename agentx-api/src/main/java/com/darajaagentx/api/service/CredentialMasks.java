package com.darajaagentx.api.service;

/**
 * Masked clues for saved secrets, so a person can confirm "yes, that is the one I saved" without the value ever
 * leaving the server. A mask never has enough characters to rebuild a secret: a consumer key or secret shows its
 * last four, a passkey its first and last four, and an initiator password shows only dots.
 */
public final class CredentialMasks {

    private static final char DOT = '•';

    private CredentialMasks() {
    }

    /** For example {@code AbC••••3456}. */
    public static String key(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.length() < 10) {
            return dots(value.length() - Math.min(2, value.length() - 1)) + tail(value, 2);
        }
        return value.substring(0, 3) + dots(4) + tail(value, 4);
    }

    /** For example {@code ••••••••3456}. */
    public static String secret(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.length() < 10 ? dots(value.length()) : dots(8) + tail(value, 4);
    }

    /** For example {@code bfb2••••c919}. */
    public static String passkey(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.length() < 16 ? dots(value.length()) : value.substring(0, 4) + dots(4) + tail(value, 4);
    }

    /** Only dots, at most twelve. Not a single character of a password is shown. */
    public static String password(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return dots(Math.min(12, value.length()));
    }

    private static String tail(String value, int n) {
        return value.substring(Math.max(0, value.length() - n));
    }

    private static String dots(int n) {
        return String.valueOf(DOT).repeat(Math.max(0, n));
    }
}
