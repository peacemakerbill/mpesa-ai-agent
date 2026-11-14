package com.darajaagentx.api.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Production refuses development only settings, staging only warns, development is left alone. */
class EnvironmentGuardTest {

    private static final String KEY = "Yq3v0pZk8Lw2nXc9Rt5uBm7Hd1Fs4GjA";
    private static final String SECRET = "k9TzP2mQx7LcV4nB8wRfH1sJd6YgA3uE5oXiC0qZ";

    private static EnvironmentGuard.Settings good() {
        return new EnvironmentGuard.Settings("sms", "", KEY, SECRET, "https://api.agentx.co.ke", "smtp",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "database", false);
    }

    private static EnvironmentGuard.Settings with(String codeDelivery, String cors, String internalKey, String tokenSecret, String url,
                                                  String mail, String vault, String storage, boolean live) {
        return new EnvironmentGuard.Settings(codeDelivery, cors, internalKey, tokenSecret, url, mail, vault, storage, live);
    }

    @Test
    void aProperlySetUpProductionHasNothingToFix() {
        EnvironmentGuard.Findings f = EnvironmentGuard.check(AppEnvironment.PRODUCTION, good());
        assertThat(f.blockers()).isEmpty();
        assertThat(f.notes()).isEmpty();
        assertThatCode(() -> EnvironmentGuard.enforce(AppEnvironment.PRODUCTION, f)).doesNotThrowAnyException();
    }

    @Test
    void developmentIsNeverChecked() {
        EnvironmentGuard.Findings f = EnvironmentGuard.check(AppEnvironment.DEVELOPMENT,
                with("dev", "http://localhost:8000", "change-me", "change-me", "http://localhost:8084", "log", "", "local", true));
        assertThat(f.blockers()).isEmpty();
        assertThat(f.notes()).isEmpty();
    }

    @Test
    void codeDeliveryDevAndCorsStopProduction() {
        EnvironmentGuard.Findings f = EnvironmentGuard.check(AppEnvironment.PRODUCTION,
                with("dev", "http://localhost:8000", KEY, SECRET, "https://api.agentx.co.ke", "smtp", "x", "database", false));
        assertThat(f.blockers()).hasSize(2);
        assertThat(f.blockers().get(0)).contains("CODE_DELIVERY=dev");
        assertThat(f.blockers().get(1)).contains("CORS_ALLOWED_ORIGINS");
        assertThatThrownBy(() -> EnvironmentGuard.enforce(AppEnvironment.PRODUCTION, f))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_ENV=production")
                .hasMessageContaining("CODE_DELIVERY=dev")
                .hasMessageContaining("CORS_ALLOWED_ORIGINS");
    }

    @Test
    void exampleOrShortSecretsStopProduction() {
        assertThat(EnvironmentGuard.check(AppEnvironment.PRODUCTION,
                with("sms", "", "change-me-to-a-long-random-string", SECRET, "https://a.co.ke", "smtp", "x", "database", false)).blockers())
                .singleElement().asString().contains("INTERNAL_KEY");
        assertThat(EnvironmentGuard.check(AppEnvironment.PRODUCTION,
                with("sms", "", "short", SECRET, "https://a.co.ke", "smtp", "x", "database", false)).blockers())
                .singleElement().asString().contains("INTERNAL_KEY");
        assertThat(EnvironmentGuard.check(AppEnvironment.PRODUCTION,
                with("sms", "", KEY, "your-token-secret-goes-here-at-least-32", "https://a.co.ke", "smtp", "x", "database", false)).blockers())
                .singleElement().asString().contains("AGENTX_TOKEN_SECRET");
    }

    @Test
    void thePublicAddressMustBeHttpsAndNotThisMachine() {
        for (String bad : new String[]{"", "http://api.agentx.co.ke", "https://localhost:8084", "https://127.0.0.1", "https://my-pc.local", "not a url"}) {
            assertThat(EnvironmentGuard.check(AppEnvironment.PRODUCTION,
                    with("sms", "", KEY, SECRET, bad, "smtp", "x", "database", false)).blockers())
                    .as(bad).singleElement().asString().contains("AGENTX_PUBLIC_BASE_URL");
        }
        assertThat(EnvironmentGuard.check(AppEnvironment.PRODUCTION,
                with("sms", "", KEY, SECRET, "https://agentx-api.onrender.com", "smtp", "x", "database", false)).blockers()).isEmpty();
    }

    @Test
    void thingsThatOnlyNeedAttentionAreNotesAndDoNotStopProduction() {
        EnvironmentGuard.Findings f = EnvironmentGuard.check(AppEnvironment.PRODUCTION,
                with("log", "", KEY, SECRET, "https://a.co.ke", "log", "", "local", false));
        assertThat(f.blockers()).isEmpty();
        assertThat(f.notes()).hasSize(4);
        assertThatCode(() -> EnvironmentGuard.enforce(AppEnvironment.PRODUCTION, f)).doesNotThrowAnyException();
    }

    @Test
    void stagingReportsTheSameProblemsButStarts() {
        EnvironmentGuard.Findings f = EnvironmentGuard.check(AppEnvironment.STAGING,
                with("dev", "http://localhost:8000", "change-me", SECRET, "http://localhost:8084", "log", "", "local", false));
        assertThat(f.blockers()).hasSize(4);
        assertThatCode(() -> EnvironmentGuard.enforce(AppEnvironment.STAGING, f)).doesNotThrowAnyException();
    }

    @Test
    void liveDarajaOutsideProductionIsMentioned() {
        assertThat(EnvironmentGuard.check(AppEnvironment.STAGING, with("sms", "", KEY, SECRET, "https://a.co.ke", "smtp", "x", "database", true)).notes())
                .singleElement().asString().contains("AGENTX_ALLOW_LIVE_DARAJA");
        assertThat(EnvironmentGuard.check(AppEnvironment.PRODUCTION, with("sms", "", KEY, SECRET, "https://a.co.ke", "smtp", "x", "database", true)).notes())
                .isEmpty();
    }

    @Test
    void nullSettingsDoNotBreakTheCheck() {
        EnvironmentGuard.Findings f = EnvironmentGuard.check(AppEnvironment.PRODUCTION, with(null, null, null, null, null, null, null, null, false));
        assertThat(f.blockers()).hasSize(3);
    }
}
