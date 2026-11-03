package com.darajaagentx.api.dto;

import com.darajaagentx.api.model.BusinessAccount;
import com.darajaagentx.api.model.DarajaEnvironment;
import com.darajaagentx.api.model.ShortcodeType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A business account as the phone sees it. There are no secrets in here, only whether each one is saved and the
 * last four characters of the consumer key.
 */
public record BusinessAccountView(
        UUID id,
        String label,
        DarajaEnvironment environment,
        ShortcodeType shortcodeType,
        String shortcode,
        String tillNumber,
        String accountReference,
        UUID parentId,
        /** True for a child that has no credentials of its own: it uses the head office's. */
        boolean inheritsCredentials,
        /** True for a child that has its own shortcode (a store number), not the head office's. */
        boolean ownShortcode,
        /** The head office's shortcode, for a child. Empty for a head office. */
        String headShortcode,
        /** Where each group of credentials comes from. */
        CredentialSources credentialSources,
        String consumerKeyHint,
        boolean hasConsumerSecret,
        String initiatorName,
        boolean hasInitiatorPassword,
        boolean hasPasskey,
        List<Capability> capabilities,
        Instant verifiedAt,
        Instant createdAt
) {

    /** For each group of credentials: OWN (saved on this account), HEAD (the head office's) or NONE (not saved anywhere). */
    public record CredentialSources(String app, String passkey, String initiator) {
    }

    /** One thing the saved credentials allow, for example STK Push. */
    public record Capability(String code, String label, boolean ready, String needs) {
    }

    /**
     * @param head the head account when {@code a} is a child, otherwise null
     */
    public static BusinessAccountView of(BusinessAccount a, BusinessAccount head) {
        boolean ownCode = head != null && !a.getShortcode().equals(head.getShortcode());
        BusinessAccount app = pick(a, head, a.hasConsumerCredentials(), head != null && head.hasConsumerCredentials());
        BusinessAccount ini = pick(a, head, a.hasInitiator(), head != null && head.hasInitiator());
        // A passkey belongs to one shortcode, so a child with its own shortcode never uses the head office's.
        BusinessAccount pk = pick(a, head, a.hasPasskey(), !ownCode && head != null && head.hasPasskey());
        CredentialSources sources = new CredentialSources(from(a, app), from(a, pk), from(a, ini));
        boolean holdsOwn = a.holdsOwnCredentials();
        return new BusinessAccountView(a.getId(), a.getLabel(), a.getEnvironment(), a.getShortcodeType(), a.getShortcode(),
                a.getTillNumber(), a.getAccountReference(), a.getParentId(), a.isBranch() && !holdsOwn, ownCode,
                head == null ? "" : head.getShortcode(), sources,
                app == null ? null : app.getConsumerKeyHint(), app != null && app.getConsumerSecretEnc() != null,
                ini == null ? null : ini.getInitiatorName(), ini != null && ini.getInitiatorPasswordEnc() != null, pk != null,
                capabilities(app != null, pk != null, ini != null), app == null ? null : app.getVerifiedAt(), a.getCreatedAt());
    }

    private static BusinessAccount pick(BusinessAccount a, BusinessAccount head, boolean own, boolean fromHead) {
        return own ? a : (fromHead ? head : null);
    }

    private static String from(BusinessAccount a, BusinessAccount source) {
        return source == null ? "NONE" : (source == a ? "OWN" : "HEAD");
    }

    static List<Capability> capabilities(boolean creds, boolean passkey, boolean initiator) {
        String key = "consumer key and secret";
        String pk = "STK passkey";
        String init = "initiator name and password";
        List<Capability> list = new ArrayList<>();
        list.add(new Capability("STK_PUSH", "STK Push", creds && passkey, !creds ? key : pk));
        list.add(new Capability("STK_QUERY", "STK Push query", creds && passkey, !creds ? key : pk));
        list.add(new Capability("DYNAMIC_QR", "Dynamic QR", creds, key));
        list.add(new Capability("C2B", "Customer payments (C2B)", creds, key));
        list.add(new Capability("PULL", "Pull transactions", creds, key));
        list.add(new Capability("B2C", "Pay out to phones (B2C)", creds && initiator, !creds ? key : init));
        list.add(new Capability("B2B", "Business to business (B2B)", creds && initiator, !creds ? key : init));
        list.add(new Capability("B2POCHI", "Pay to Pochi la Biashara", creds && initiator, !creds ? key : init));
        list.add(new Capability("BALANCE", "Account balance", creds && initiator, !creds ? key : init));
        list.add(new Capability("STATUS", "Transaction status", creds && initiator, !creds ? key : init));
        list.add(new Capability("REVERSAL", "Reversals", creds && initiator, !creds ? key : init));
        return list;
    }
}
