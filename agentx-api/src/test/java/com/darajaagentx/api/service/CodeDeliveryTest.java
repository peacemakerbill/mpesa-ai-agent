package com.darajaagentx.api.service;

import com.darajaagentx.api.config.AppEnvironment;
import com.darajaagentx.api.config.EnvironmentPolicy;
import com.darajaagentx.api.config.SmsProperties;
import com.darajaagentx.api.exception.CodeDeliveryException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodeDeliveryTest {

    private static final SmsProperties NO_KEY = new SmsProperties("sandbox", "", "", "http://localhost:9");
    private static final SmsProperties UNREACHABLE = new SmsProperties("sandbox", "key", "", "http://localhost:9");
    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);

    private static CodeDelivery delivery(String mode, String show, AppEnvironment env, SmsProperties sms) {
        return new CodeDelivery(TestSupport.propsWithDelivery(mode), sms, "Test", new EnvironmentPolicy(env, false), show);
    }

    @Test
    void theDeliveryNameIsReadLeniently() {
        assertThat(CodeDelivery.parse(null)).isEqualTo(CodeDelivery.Mode.LOG);
        assertThat(CodeDelivery.parse("  ")).isEqualTo(CodeDelivery.Mode.LOG);
        assertThat(CodeDelivery.parse("DEV")).isEqualTo(CodeDelivery.Mode.DEV);
        assertThat(CodeDelivery.parse(" sms ")).isEqualTo(CodeDelivery.Mode.SMS);
        assertThat(CodeDelivery.parse("whatsapp")).isEqualTo(CodeDelivery.Mode.LOG);
    }

    @Test
    void developmentAndStagingShowTheCodeByDefault() {
        for (AppEnvironment env : new AppEnvironment[]{AppEnvironment.DEVELOPMENT, AppEnvironment.STAGING}) {
            CodeDelivery d = delivery("log", "auto", env, NO_KEY);
            assertThat(d.showsCodes()).as(env.label()).isTrue();
            assertThat(d.deliver("254712345678", "123456", TEN_MINUTES).devCode()).as(env.label()).isEqualTo("123456");
        }
    }

    @Test
    void turningTheSettingOffNeverHandsTheCodeBack() {
        for (String off : new String[]{"false", "off", "no"}) {
            CodeDelivery d = delivery("log", off, AppEnvironment.STAGING, NO_KEY);
            assertThat(d.showsCodes()).as(off).isFalse();
            assertThat(d.deliver("254712345678", "123456", TEN_MINUTES).devCode()).as(off).isNull();
        }
    }

    @Test
    void productionNeverShowsTheCodeWhateverIsSet() {
        for (String setting : new String[]{"auto", "true", "on", ""}) {
            CodeDelivery d = delivery("dev", setting, AppEnvironment.PRODUCTION, NO_KEY);
            assertThat(d.showsCodes()).as(setting).isFalse();
            assertThat(d.deliver("254712345678", "123456", TEN_MINUTES).devCode()).as(setting).isNull();
        }
    }

    @Test
    void theOldDevNameStillShowsTheCodeAndSendsNoText() {
        CodeDelivery delivery = new CodeDelivery(TestSupport.propsWithDelivery("dev"), NO_KEY, "Test");

        assertThat(delivery.mode()).isEqualTo(CodeDelivery.Mode.DEV);
        CodeDelivery.Result r = delivery.deliver("254712345678", "123456", TEN_MINUTES);
        assertThat(r.devCode()).isEqualTo("123456");
        assertThat(r.smsSent()).isFalse();
    }

    @Test
    void smsModeWithTheCodeHiddenFailsClearlyWithoutAKey() {
        CodeDelivery delivery = delivery("sms", "false", AppEnvironment.STAGING, NO_KEY);

        assertThatThrownBy(() -> delivery.deliver("254712345678", "123456", TEN_MINUTES))
                .isInstanceOf(CodeDeliveryException.class).hasMessageContaining("SMS_API_KEY");
    }

    @Test
    void anUnreachableSmsProviderIsReportedWithoutLeakingDetails() {
        CodeDelivery delivery = delivery("sms", "false", AppEnvironment.PRODUCTION, UNREACHABLE);

        assertThatThrownBy(() -> delivery.deliver("254712345678", "123456", TEN_MINUTES))
                .isInstanceOf(CodeDeliveryException.class).hasMessageContaining("could not be sent")
                .hasMessageNotContaining("localhost");
    }

    @Test
    void whenTheCodeIsShownAFailedTextDoesNotBlockSigningIn() {
        CodeDelivery noKey = delivery("sms", "auto", AppEnvironment.STAGING, NO_KEY);
        CodeDelivery down = delivery("sms", "true", AppEnvironment.DEVELOPMENT, UNREACHABLE);

        for (CodeDelivery d : new CodeDelivery[]{noKey, down}) {
            CodeDelivery.Result r = d.deliver("254712345678", "123456", TEN_MINUTES);
            assertThat(r.devCode()).isEqualTo("123456");
            assertThat(r.smsSent()).isFalse();
        }
    }

    @Test
    void aSentTextIsReportedAndTheCodeIsShownOnlyWhenAllowed() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/version1/messaging", exchange -> {
            calls.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });
        server.start();
        try {
            SmsProperties live = new SmsProperties("sandbox", "key", "", "http://127.0.0.1:" + server.getAddress().getPort());

            CodeDelivery.Result both = delivery("sms", "auto", AppEnvironment.STAGING, live).deliver("254712345678", "123456", TEN_MINUTES);
            assertThat(both.smsSent()).isTrue();
            assertThat(both.devCode()).isEqualTo("123456");

            CodeDelivery.Result smsOnly = delivery("sms", "false", AppEnvironment.STAGING, live).deliver("254712345678", "654321", TEN_MINUTES);
            assertThat(smsOnly.smsSent()).isTrue();
            assertThat(smsOnly.devCode()).isNull();
            assertThat(calls.get()).isEqualTo(2);
        } finally {
            server.stop(0);
        }
    }
}
