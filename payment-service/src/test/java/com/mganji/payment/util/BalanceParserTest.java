package com.mganji.payment.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BalanceParserTest {

    @Test
    void formatsEachAccountWithThousandsSeparators() {
        String raw = "Working Account|KES|700000.00|700000.00|0.00|0.00&Utility Account|KES|228037.00|228037.00|0.00|0.00";

        Map<String, String> result = BalanceParser.parse(raw);

        assertThat(result).containsEntry("Working Account", "KES 700,000.00")
                .containsEntry("Utility Account", "KES 228,037.00");
        assertThat(result.keySet()).containsExactly("Working Account", "Utility Account");
    }

    @Test
    void ignoresBlankAndBrokenInput() {
        assertThat(BalanceParser.parse(null)).isEmpty();
        assertThat(BalanceParser.parse("  ")).isEmpty();
        assertThat(BalanceParser.parse("garbage")).isEmpty();
    }

    @Test
    void keepsUnparseableAmountsAsText() {
        assertThat(BalanceParser.parse("Working Account|KES|n/a|n/a")).containsEntry("Working Account", "KES n/a");
    }
}
