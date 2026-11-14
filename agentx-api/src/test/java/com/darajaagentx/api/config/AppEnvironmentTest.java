package com.darajaagentx.api.config;

import com.darajaagentx.api.model.DarajaEnvironment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** APP_ENV is read forgivingly, a typo stops the start, and live Daraja stays off outside production. */
class AppEnvironmentTest {

    @Test
    void theThreeEnvironmentsAndTheirOtherNamesAreRecognised() {
        assertThat(AppEnvironment.parse("development")).isEqualTo(AppEnvironment.DEVELOPMENT);
        assertThat(AppEnvironment.parse(" Dev ")).isEqualTo(AppEnvironment.DEVELOPMENT);
        assertThat(AppEnvironment.parse("local")).isEqualTo(AppEnvironment.DEVELOPMENT);
        assertThat(AppEnvironment.parse("staging")).isEqualTo(AppEnvironment.STAGING);
        assertThat(AppEnvironment.parse("UAT")).isEqualTo(AppEnvironment.STAGING);
        assertThat(AppEnvironment.parse("qa")).isEqualTo(AppEnvironment.STAGING);
        assertThat(AppEnvironment.parse("production")).isEqualTo(AppEnvironment.PRODUCTION);
        assertThat(AppEnvironment.parse("PROD")).isEqualTo(AppEnvironment.PRODUCTION);
        assertThat(AppEnvironment.parse("live")).isEqualTo(AppEnvironment.PRODUCTION);
    }

    @Test
    void blankMeansDevelopmentAndAnUnknownWordIsNotGuessed() {
        assertThat(AppEnvironment.parse(null)).isEqualTo(AppEnvironment.DEVELOPMENT);
        assertThat(AppEnvironment.parse("  ")).isEqualTo(AppEnvironment.DEVELOPMENT);
        assertThat(AppEnvironment.parse("prodution")).isNull();
    }

    @Test
    void aTypoStopsTheStartInsteadOfRunningAsSomethingElse() {
        assertThatThrownBy(() -> EnvironmentConfig.create("prodution", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("development, staging or production")
                .hasMessageContaining("prodution");
        assertThat(EnvironmentConfig.create("uat", false).env()).isEqualTo(AppEnvironment.STAGING);
    }

    @Test
    void onlyProductionHasNoEmailTag() {
        assertThat(AppEnvironment.PRODUCTION.mailTag()).isEmpty();
        assertThat(AppEnvironment.STAGING.mailTag()).isEqualTo("[STAGING] ");
        assertThat(AppEnvironment.DEVELOPMENT.mailTag()).isEqualTo("[DEV] ");
        assertThat(AppEnvironment.STAGING.label()).isEqualTo("staging");
    }

    @Test
    void liveDarajaIsOnlyReachableFromProductionUnlessSwitchedOnOnPurpose() {
        EnvironmentPolicy staging = new EnvironmentPolicy(AppEnvironment.STAGING, false);
        assertThat(staging.liveDarajaBlocked(DarajaEnvironment.PRODUCTION)).isTrue();
        assertThat(staging.liveDarajaBlocked(DarajaEnvironment.SANDBOX)).isFalse();
        assertThat(new EnvironmentPolicy(AppEnvironment.DEVELOPMENT, false).liveDarajaBlocked(DarajaEnvironment.PRODUCTION)).isTrue();
        assertThat(new EnvironmentPolicy(AppEnvironment.STAGING, true).liveDarajaBlocked(DarajaEnvironment.PRODUCTION)).isFalse();
        assertThat(EnvironmentPolicy.production().liveDarajaBlocked(DarajaEnvironment.PRODUCTION)).isFalse();
        assertThat(EnvironmentPolicy.production().liveDarajaBlocked(DarajaEnvironment.SANDBOX)).isFalse();
        assertThat(staging.liveDarajaBlockedMessage()).contains("staging").contains("no real money");
    }

    @Test
    void aMissingEnvironmentInThePolicyMeansDevelopment() {
        assertThat(new EnvironmentPolicy(null, false).env()).isEqualTo(AppEnvironment.DEVELOPMENT);
    }
}
