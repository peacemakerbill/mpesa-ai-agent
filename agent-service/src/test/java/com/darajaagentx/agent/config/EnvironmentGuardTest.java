package com.darajaagentx.agent.config;

import com.darajaagentx.agent.config.EnvironmentGuard.Env;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Production refuses development only settings, staging only warns, development is left alone. */
class EnvironmentGuardTest {

    private static final String KEY = "Yq3v0pZk8Lw2nXc9Rt5uBm7Hd1Fs4GjA";

    @Test
    void theThreeEnvironmentsAndTheirOtherNamesAreRecognised() {
        assertThat(Env.parse(null)).isEqualTo(Env.DEVELOPMENT);
        assertThat(Env.parse("  ")).isEqualTo(Env.DEVELOPMENT);
        assertThat(Env.parse("Dev")).isEqualTo(Env.DEVELOPMENT);
        assertThat(Env.parse("uat")).isEqualTo(Env.STAGING);
        assertThat(Env.parse("staging")).isEqualTo(Env.STAGING);
        assertThat(Env.parse("PROD")).isEqualTo(Env.PRODUCTION);
        assertThat(Env.parse("prodution")).isNull();
    }

    @Test
    void aProperlySetUpProductionHasNothingToFix() {
        assertThat(EnvironmentGuard.blockers(Env.PRODUCTION, "redis", "", KEY, "https://api.agentx.co.ke")).isEmpty();
    }

    @Test
    void developmentIsNeverChecked() {
        assertThat(EnvironmentGuard.blockers(Env.DEVELOPMENT, "memory", "http://localhost:8000", "change-me", "http://localhost:8084")).isEmpty();
    }

    @Test
    void productionRefusesEachDevelopmentOnlySetting() {
        assertThat(EnvironmentGuard.blockers(Env.PRODUCTION, "memory", "", KEY, "https://a.co.ke")).singleElement().asString().contains("AGENT_STORE");
        assertThat(EnvironmentGuard.blockers(Env.PRODUCTION, "redis", "http://localhost:8000", KEY, "https://a.co.ke")).singleElement().asString().contains("CORS_ALLOWED_ORIGINS");
        assertThat(EnvironmentGuard.blockers(Env.PRODUCTION, "redis", "", "change-me-to-a-long-random-string", "https://a.co.ke")).singleElement().asString().contains("INTERNAL_KEY");
        assertThat(EnvironmentGuard.blockers(Env.PRODUCTION, "redis", "", "short", "https://a.co.ke")).singleElement().asString().contains("INTERNAL_KEY");
        assertThat(EnvironmentGuard.blockers(Env.PRODUCTION, "redis", "", KEY, "http://localhost:8084")).singleElement().asString().contains("AGENTX_PUBLIC_BASE_URL");
        assertThat(EnvironmentGuard.blockers(Env.PRODUCTION, "redis", "", KEY, "https://localhost:8084")).singleElement().asString().contains("AGENTX_PUBLIC_BASE_URL");
    }

    @Test
    void stagingReportsTheSameProblems() {
        assertThat(EnvironmentGuard.blockers(Env.STAGING, "memory", "http://localhost:8000", "change-me", "http://localhost:8084")).hasSize(4);
    }

    @Test
    void productionStopsTheStartAndStagingDoesNot() {
        EnvironmentGuard production = new EnvironmentGuard("production", "memory", "", KEY, "https://a.co.ke");
        assertThatThrownBy(production::afterPropertiesSet).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_ENV=production").hasMessageContaining("AGENT_STORE");
        assertThatCode(new EnvironmentGuard("staging", "memory", "", KEY, "https://a.co.ke")::afterPropertiesSet).doesNotThrowAnyException();
        assertThatCode(new EnvironmentGuard("", "memory", "", KEY, "")::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void aTypoStopsTheStart() {
        assertThatThrownBy(new EnvironmentGuard("prodution", "redis", "", KEY, "https://a.co.ke")::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("development, staging or production");
    }
}
