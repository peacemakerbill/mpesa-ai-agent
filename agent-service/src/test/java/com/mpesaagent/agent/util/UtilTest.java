package com.mpesaagent.agent.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UtilTest {

    @Test
    void moneyIsFormattedWithSeparators() {
        assertThat(Money.kes(1500)).isEqualTo("KES 1,500");
        assertThat(Money.kes(1)).isEqualTo("KES 1");
        assertThat(Money.kes(new BigDecimal("150.00"))).isEqualTo("KES 150");
        assertThat(Money.kes(new BigDecimal("1234.5"))).isEqualTo("KES 1,234.50");
        assertThat(Money.kes((BigDecimal) null)).isEqualTo("KES 0");
    }

    @Test
    void phonesNormaliseValidateAndMask() {
        assertThat(Phones.normalize("0712 345 678")).isEqualTo("254712345678");
        assertThat(Phones.normalize("+254712345678")).isEqualTo("254712345678");
        assertThat(Phones.normalize("712345678")).isEqualTo("254712345678");
        assertThat(Phones.isKenyanMobile("254712345678")).isTrue();
        assertThat(Phones.isKenyanMobile("254112345678")).isTrue();
        assertThat(Phones.isKenyanMobile("255712345678")).isFalse();
        assertThat(Phones.isKenyanMobile("25471234567")).isFalse();
        assertThat(Phones.mask("254712345678")).isEqualTo("254712***678");
        assertThat(Phones.mask(null)).isEqualTo("***");
    }

    @Test
    void markdownBecomesPlainWordsForSpeech() {
        String plain = PlainText.strip("# Your payment\n\n**Amount:** KES 500\n\n- first\n- second\n\n[Pay](https://example.com/pay)");

        assertThat(plain).isEqualTo("Your payment Amount: KES 500 first second Pay");
        assertThat(PlainText.strip(null)).isEmpty();
        assertThat(PlainText.strip("   ")).isEmpty();
    }
}
