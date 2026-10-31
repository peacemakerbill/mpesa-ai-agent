package com.mganji.agent.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public final class Money {

    private Money() {
    }

    /** 1500 becomes "KES 1,500". */
    public static String kes(long amount) {
        return String.format(Locale.ENGLISH, "KES %,d", amount);
    }

    /** 150 becomes "KES 150", 150.5 becomes "KES 150.50". */
    public static String kes(BigDecimal amount) {
        if (amount == null) {
            return "KES 0";
        }
        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
        if (scaled.stripTrailingZeros().scale() <= 0) {
            return kes(scaled.longValue());
        }
        return String.format(Locale.ENGLISH, "KES %,.2f", scaled);
    }
}
