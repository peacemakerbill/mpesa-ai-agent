package com.darajaagentx.api.service;

import java.net.URI;
import java.util.Locale;
import java.util.Map;

/**
 * Small rules about the address Safaricom calls with the answer to an STK Push, and what the answer's code means in plain words.
 */
public final class StkCallback {

    /** Safaricom's STK result codes that a customer can understand. Anything else shows Safaricom's own description. */
    private static final Map<String, String> REASONS = Map.of(
            "1", "The customer's M-Pesa balance was not enough.",
            "1001", "Another M-Pesa request was already open on that phone. Wait a minute and try again.",
            "1019", "The request expired before the customer answered.",
            "1025", "The prompt could not be shown on that phone. Try again.",
            "1032", "The customer cancelled the request on their phone.",
            "1037", "The customer did not answer the prompt in time, or the phone could not be reached.",
            "2001", "The customer entered a wrong PIN.",
            "9999", "Safaricom could not send the prompt to that phone. Try again.");

    private StkCallback() {
    }

    /** The address without spaces or a closing slash. Blank when nothing was given. */
    public static String normalize(String url) {
        return url == null ? "" : url.strip().replaceAll("/+$", "");
    }

    /**
     * What is wrong with an address Safaricom is meant to call, or null when it looks usable. Safaricom cannot reach a laptop address,
     * and it only calls https.
     */
    public static String problem(String url) {
        String clean = normalize(url);
        if (clean.isEmpty()) {
            return "no address is set";
        }
        URI uri;
        try {
            uri = URI.create(clean);
        } catch (IllegalArgumentException e) {
            return "\"" + clean + "\" is not a web address";
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https") || host.isEmpty()) {
            return "\"" + clean + "\" is not a web address";
        }
        if (host.equals("localhost") || host.endsWith(".localhost") || host.equals("0.0.0.0") || host.startsWith("127.")
                || host.startsWith("192.168.") || host.startsWith("10.") || !host.contains(".")) {
            return "Safaricom cannot reach " + host + " from the internet";
        }
        if (!scheme.equals("https")) {
            return "Safaricom only calls https addresses";
        }
        return null;
    }

    /** Why an STK Push did not end in a payment, in words the owner can act on. */
    public static String failureText(String code, String description) {
        String reason = code == null ? null : REASONS.get(code.strip());
        if (reason == null) {
            reason = description == null || description.isBlank() ? "The customer did not complete the payment." : description.strip();
            if (!reason.endsWith(".")) {
                reason = reason + ".";
            }
        }
        return code == null || code.isBlank() ? reason : reason + " (code " + code.strip() + ")";
    }
}
