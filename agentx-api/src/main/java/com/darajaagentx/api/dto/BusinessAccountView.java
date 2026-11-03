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
        /** True for a branch: the credentials below are the parent's. */
        boolean inheritsCredentials,
        String consumerKeyHint,
        boolean hasConsumerSecret,
        String initiatorName,
        boolean hasInitiatorPassword,
        boolean hasPasskey,
        List<Capability> capabilities,
        Instant verifiedAt,
        Instant createdAt
) {

    /** One thing the saved credentials allow, for example STK Push. */
    public record Capability(String code, String label, boolean ready, String needs) {
    }

    /** @param credentialSource the account whose credentials apply: the same account, or the parent for a branch */
    public static BusinessAccountView of(BusinessAccount a, BusinessAccount credentialSource) {
        BusinessAccount src = credentialSource == null ? a : credentialSource;
        boolean creds = src.hasConsumerCredentials();
        boolean initiator = src.hasInitiator();
        boolean passkey = src.hasPasskey();
        return new BusinessAccountView(a.getId(), a.getLabel(), a.getEnvironment(), a.getShortcodeType(), a.getShortcode(),
                a.getTillNumber(), a.getAccountReference(), a.getParentId(), a.isBranch(),
                src.getConsumerKeyHint(), src.getConsumerSecretEnc() != null,
                src.getInitiatorName(), src.getInitiatorPasswordEnc() != null, passkey,
                capabilities(creds, passkey, initiator), src.getVerifiedAt(), a.getCreatedAt());
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
