package com.mganji.ganji.service;

import com.mganji.ganji.config.SmsProperties;
import com.mganji.ganji.exception.CodeDeliveryException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodeDeliveryTest {

    private static final SmsProperties NO_KEY = new SmsProperties("sandbox", "", "", "http://localhost:9");

    @Test
    void theDeliveryNameIsReadLeniently() {
        assertThat(CodeDelivery.parse(null)).isEqualTo(CodeDelivery.Mode.LOG);
        assertThat(CodeDelivery.parse("  ")).isEqualTo(CodeDelivery.Mode.LOG);
        assertThat(CodeDelivery.parse("DEV")).isEqualTo(CodeDelivery.Mode.DEV);
        assertThat(CodeDelivery.parse(" sms ")).isEqualTo(CodeDelivery.Mode.SMS);
        assertThat(CodeDelivery.parse("whatsapp")).isEqualTo(CodeDelivery.Mode.LOG);
    }

    @Test
    void logModeNeverHandsTheCodeBack() {
        CodeDelivery delivery = new CodeDelivery(TestSupport.propsWithDelivery("log"), NO_KEY, "Test");

        assertThat(delivery.deliver("254712345678", "123456", Duration.ofMinutes(10)).devCode()).isNull();
    }

    @Test
    void devModeReturnsTheCodeToTheApp() {
        CodeDelivery delivery = new CodeDelivery(TestSupport.propsWithDelivery("dev"), NO_KEY, "Test");

        assertThat(delivery.mode()).isEqualTo(CodeDelivery.Mode.DEV);
        assertThat(delivery.deliver("254712345678", "123456", Duration.ofMinutes(10)).devCode()).isEqualTo("123456");
    }

    @Test
    void smsModeWithoutAKeyFailsClearlyInsteadOfSilently() {
        CodeDelivery delivery = new CodeDelivery(TestSupport.propsWithDelivery("sms"), NO_KEY, "Test");

        assertThatThrownBy(() -> delivery.deliver("254712345678", "123456", Duration.ofMinutes(10)))
                .isInstanceOf(CodeDeliveryException.class).hasMessageContaining("SMS_API_KEY");
    }

    @Test
    void anUnreachableSmsProviderIsReportedWithoutLeakingDetails() {
        SmsProperties unreachable = new SmsProperties("sandbox", "key", "", "http://localhost:9");
        CodeDelivery delivery = new CodeDelivery(TestSupport.propsWithDelivery("sms"), unreachable, "Test");

        assertThatThrownBy(() -> delivery.deliver("254712345678", "123456", Duration.ofMinutes(10)))
                .isInstanceOf(CodeDeliveryException.class).hasMessageContaining("could not be sent")
                .hasMessageNotContaining("localhost");
    }
}
