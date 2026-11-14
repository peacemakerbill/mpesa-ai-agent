package com.darajaagentx.api.service;

import com.darajaagentx.api.config.AppEnvironment;
import com.darajaagentx.api.config.EnvironmentPolicy;
import com.darajaagentx.api.config.SmsProperties;
import com.darajaagentx.api.exception.CodeDeliveryException;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

    /** Everything this class writes to the log while [work] runs. */
    private static List<String> logged(Runnable work) {
        Logger logger = (Logger) LoggerFactory.getLogger(CodeDelivery.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            work.run();
            return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void aCodeThatIsShownInTheAppIsNotWrittenToTheLog() {
        for (String mode : new String[]{"log", "dev"}) {
            CodeDelivery shown = delivery(mode, "auto", AppEnvironment.STAGING, NO_KEY);
            List<String> lines = logged(() -> shown.deliver("254712345678", "482913", TEN_MINUTES));
            assertThat(lines).as(mode).noneMatch(l -> l.contains("482913"));
        }
    }

    @Test
    void aCodeWithNowhereElseToGoIsWrittenToTheLog() {
        CodeDelivery hidden = delivery("log", "false", AppEnvironment.DEVELOPMENT, NO_KEY);

        List<String> lines = logged(() -> hidden.deliver("254712345678", "482913", TEN_MINUTES));

        assertThat(lines).anyMatch(l -> l.contains("482913"));
    }

    @Test
    void aCodeSentBySmsIsNeverWrittenToTheLog() {
        CodeDelivery noKey = delivery("sms", "auto", AppEnvironment.STAGING, NO_KEY);
        CodeDelivery down = delivery("sms", "false", AppEnvironment.PRODUCTION, UNREACHABLE);

        List<String> lines = logged(() -> {
            noKey.deliver("254712345678", "482913", TEN_MINUTES);
            try {
                down.deliver("254712345678", "482913", TEN_MINUTES);
            } catch (CodeDeliveryException expected) {
                // the failure is the point of this part
            }
        });

        assertThat(lines).noneMatch(l -> l.contains("482913"));
    }

    @Test
    void theAppHashIsKeptOnlyWhenItHasTheRightShape() {
        assertThat(CodeDelivery.cleanHash("FA+9qCX9VSu")).isEqualTo("FA+9qCX9VSu");
        assertThat(CodeDelivery.cleanHash("  FA+9qCX9VSu ")).isEqualTo("FA+9qCX9VSu");
        for (String bad : new String[]{null, "", "short", "FA+9qCX9VSuX", "FA+9qCX9VS!", "FA+9qCX9VS\nu"}) {
            assertThat(CodeDelivery.cleanHash(bad)).as(String.valueOf(bad)).isNull();
        }
    }

    @Test
    void theTextEndsWithTheHashAndStaysWithinWhatAndroidAllows() {
        String plain = CodeDelivery.smsText("Daraja Agent-X", "123456", TEN_MINUTES, null);
        assertThat(plain).isEqualTo("Daraja Agent-X code: 123456. It works for 10 minutes. Never share it with anyone.");

        String withHash = CodeDelivery.smsText("Daraja Agent-X", "123456", TEN_MINUTES, "FA+9qCX9VSu");
        assertThat(withHash).startsWith("<#> Daraja Agent-X code: 123456").endsWith("\nFA+9qCX9VSu");
        assertThat(withHash.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(140);

        String longName = CodeDelivery.smsText("A very long business name that goes on and on and on and on and on", "123456", TEN_MINUTES, "FA+9qCX9VSu");
        assertThat(longName.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(140);
        assertThat(longName).contains("123456").endsWith("\nFA+9qCX9VSu");
    }

    @Test
    void theHashFromTheAppReachesTheProviderAtTheEndOfTheText() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/version1/messaging", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });
        server.start();
        try {
            SmsProperties live = new SmsProperties("sandbox", "key", "", "http://127.0.0.1:" + server.getAddress().getPort());
            CodeDelivery sms = delivery("sms", "false", AppEnvironment.STAGING, live);

            sms.deliver("254712345678", "123456", TEN_MINUTES, "FA+9qCX9VSu");
            String message = formField(body.get(), "message");
            assertThat(message).startsWith("<#> ").contains("123456").endsWith("\nFA+9qCX9VSu");

            sms.deliver("254712345678", "654321", TEN_MINUTES, "not a hash");
            assertThat(formField(body.get(), "message")).doesNotStartWith("<#>").contains("654321");
        } finally {
            server.stop(0);
        }
    }

    private static String formField(String form, String name) {
        for (String pair : form.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("no field " + name + " in " + form);
    }
}
