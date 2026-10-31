package com.mganji.payment.util;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BalanceParser {

    private BalanceParser() {
    }

    /**
     * Turns Safaricom's balance string into account name and amount pairs. Input looks like
     * "Working Account|KES|700000.00|700000.00|0.00|0.00&Utility Account|KES|228037.00|228037.00|0.00|0.00"
     * (name, currency, current, available, reserved, uncleared). Output: "Working Account" to "KES 700,000.00".
     */
    public static Map<String, String> parse(String raw) {
        Map<String, String> result = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String account : raw.split("&")) {
            String[] parts = account.split("\\|");
            if (parts.length < 3) {
                continue;
            }
            String name = parts[0].trim();
            String currency = parts[1].trim();
            String amount = parts.length > 3 ? parts[3].trim() : parts[2].trim();
            try {
                result.put(name, String.format("%s %,.2f", currency, new BigDecimal(amount)));
            } catch (NumberFormatException e) {
                result.put(name, currency + " " + amount);
            }
        }
        return result;
    }
}
