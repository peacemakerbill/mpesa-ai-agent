package com.mganji.payment.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentStatusTest {

    @ParameterizedTest
    @CsvSource({
            "0,SUCCESS",
            "1032,CANCELLED",
            "1037,TIMEOUT",
            "1,FAILED",
            "2001,FAILED",
            "SFC_IC0003,FAILED"
    })
    void mapsResultCodes(String code, PaymentStatus expected) {
        assertThat(PaymentStatus.fromResultCode(code)).isEqualTo(expected);
    }

    @Test
    void timeoutIsNotFinalSoALateSuccessStillCounts() {
        assertThat(PaymentStatus.TIMEOUT.isFinal()).isFalse();
        assertThat(PaymentStatus.PUSHED.isFinal()).isFalse();
        assertThat(PaymentStatus.PENDING.isFinal()).isFalse();
        assertThat(PaymentStatus.SUCCESS.isFinal()).isTrue();
        assertThat(PaymentStatus.FAILED.isFinal()).isTrue();
        assertThat(PaymentStatus.CANCELLED.isFinal()).isTrue();
    }
}
