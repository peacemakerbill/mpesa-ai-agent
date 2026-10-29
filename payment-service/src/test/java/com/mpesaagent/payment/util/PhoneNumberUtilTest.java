package com.mpesaagent.payment.util;

import com.mpesaagent.payment.exception.InvalidPaymentRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNumberUtilTest {

    @ParameterizedTest
    @CsvSource({
            "0712345678,254712345678",
            "0112345678,254112345678",
            "712345678,254712345678",
            "+254712345678,254712345678",
            "254712345678,254712345678",
            "0712 345 678,254712345678",
            "0712-345-678,254712345678"
    })
    void normalizesCommonKenyanFormats(String input, String expected) {
        assertThat(PhoneNumberUtil.normalize(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "abc", "0812345678", "07123456", "25471234567", "+1 415 555 0100"})
    void rejectsInvalidNumbers(String input) {
        assertThatThrownBy(() -> PhoneNumberUtil.normalize(input))
                .isInstanceOf(InvalidPaymentRequestException.class);
    }

    @Test
    void masksMiddleOfNumber() {
        assertThat(PhoneNumberUtil.mask("254712345678")).isEqualTo("254712***678");
        assertThat(PhoneNumberUtil.mask(null)).isEqualTo("***");
    }
}
