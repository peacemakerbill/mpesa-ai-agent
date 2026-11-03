package com.darajaagentx.api.dto;

import java.util.UUID;

/**
 * What the phone sends to add or change a business account, and what one CSV row is turned into.
 * Text fields are checked by BusinessAccountService, which gives plain messages the app can show.
 *
 * <p>When editing, an empty secret field means "keep the saved one".
 *
 * <p>A child (a store number or a till under a head office) can share the head office's shortcode, or have its own
 * shortcode. A child with its own shortcode can use the head office's credentials or its own (see {@code credentials}).
 */
public record BusinessAccountRequest(
        String label,
        String environment,
        String shortcodeType,
        String shortcode,
        String tillNumber,
        String accountReference,
        /** JSON only: the head account this branch belongs to. */
        UUID parentId,
        /** CSV only: the head shortcode this branch belongs to. */
        String parentShortcode,
        String consumerKey,
        String consumerSecret,
        String initiatorName,
        String initiatorPassword,
        String passkey,
        /** Children only: "head" to use the head office's credentials, "own" for this account's own. Empty keeps what is saved, or picks "own" when credentials are sent. */
        String credentials
) {

    @Override
    public String toString() {
        // never print secrets, even by accident in a log line
        return "BusinessAccountRequest[label=" + label + ", shortcode=" + shortcode + "]";
    }
}
