package com.darajaagentx.api.model;

import java.util.Locale;

/** A Paybill collects with an account number. A Buy Goods shortcode (a store or head office) has tills under it. */
public enum ShortcodeType {
    PAYBILL,
    BUY_GOODS;

    /** Accepts paybill, pay bill, buy goods, buy_goods, buygoods and till in any case. Returns null otherwise. */
    public static ShortcodeType parse(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim().toLowerCase(Locale.ROOT).replace('-', ' ').replace('_', ' ').replaceAll("\\s+", " ");
        return switch (t) {
            case "paybill", "pay bill" -> PAYBILL;
            case "buy goods", "buygoods", "till", "till number" -> BUY_GOODS;
            default -> null;
        };
    }
}
