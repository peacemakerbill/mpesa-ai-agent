package com.darajaagentx.api.model;

import java.util.Locale;

/**
 * What a customer can ask Daraja to do on one of their business accounts.
 *
 * <p>{@link #capability()} is the code shown in BusinessAccountView.capabilities, so an operation can only be prepared
 * when the account has the credentials it needs. Money that leaves the business always needs the owner's approval in the
 * app. Collections (STK Push) need it only when agentx.business.approve-collections is true.
 */
public enum BusinessOperationType {
    STK_PUSH("STK Push", "STK_PUSH", Kind.COLLECTION),
    STK_QUERY("STK Push query", "STK_QUERY", Kind.READ),
    DYNAMIC_QR("Dynamic QR", "DYNAMIC_QR", Kind.READ),
    PULL("Pull transactions", "PULL", Kind.READ),
    BALANCE("Account balance", "BALANCE", Kind.READ),
    STATUS("Transaction status", "STATUS", Kind.READ),
    B2C("Pay out to a phone (B2C)", "B2C", Kind.MONEY_OUT),
    B2POCHI("Pay to Pochi la Biashara", "B2POCHI", Kind.MONEY_OUT),
    B2B("Business to business (B2B)", "B2B", Kind.MONEY_OUT),
    REVERSAL("Reversal", "REVERSAL", Kind.MONEY_OUT);

    private enum Kind { READ, COLLECTION, MONEY_OUT }

    private final String label;
    private final String capability;
    private final Kind kind;

    BusinessOperationType(String label, String capability, Kind kind) {
        this.label = label;
        this.capability = capability;
        this.kind = kind;
    }

    public String label() {
        return label;
    }

    public String capability() {
        return capability;
    }

    public boolean movesMoneyOut() {
        return kind == Kind.MONEY_OUT;
    }

    public boolean requiresApproval(boolean approveCollections) {
        return kind == Kind.MONEY_OUT || (kind == Kind.COLLECTION && approveCollections);
    }

    /** True when Daraja answers the call itself, so there is nothing to wait for on a result URL. */
    public boolean answersAtOnce() {
        return this == STK_QUERY || this == DYNAMIC_QR || this == PULL;
    }

    public static BusinessOperationType parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String key = text.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        try {
            return valueOf(key);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
