package com.darajaagentx.api.service;

import com.darajaagentx.api.model.DarajaEnvironment;
import com.darajaagentx.api.model.ShortcodeType;

import java.util.UUID;

/**
 * The unlocked credentials for one business account, with a branch's missing values filled in from its parent.
 * Used by the services that call Daraja for the business. Never send this to a phone and never log it.
 */
public record ResolvedCredentials(
        UUID accountId,
        DarajaEnvironment environment,
        ShortcodeType shortcodeType,
        String shortcode,
        String tillNumber,
        String accountReference,
        String consumerKey,
        String consumerSecret,
        String initiatorName,
        String initiatorPassword,
        String passkey
) {

    /** The receiving party for STK Push: the till for Buy Goods when there is one, otherwise the shortcode. */
    public String partyB() {
        return shortcodeType == ShortcodeType.BUY_GOODS && !tillNumber.isBlank() ? tillNumber : shortcode;
    }

    public String stkTransactionType() {
        return shortcodeType == ShortcodeType.BUY_GOODS ? "CustomerBuyGoodsOnline" : "CustomerPayBillOnline";
    }

    @Override
    public String toString() {
        return "ResolvedCredentials[account=" + accountId + ", " + environment + ", shortcode=" + shortcode + ", secrets hidden]";
    }
}
