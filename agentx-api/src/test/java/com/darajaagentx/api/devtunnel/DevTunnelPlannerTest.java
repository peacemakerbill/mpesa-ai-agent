package com.darajaagentx.api.devtunnel;

import com.darajaagentx.api.config.AppEnvironment;
import com.darajaagentx.api.devtunnel.DevTunnelPlanner.Action;
import com.darajaagentx.api.devtunnel.DevTunnelPlanner.Facts;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DevTunnelPlannerTest {

    private static Facts facts(AppEnvironment env, boolean off, String stk, String base, String provider,
                               boolean zInstalled, boolean zReady, boolean nInstalled, boolean nReady) {
        return new Facts(env, off, stk, base, provider, zInstalled, zReady, nInstalled, nReady);
    }

    private static Facts dev(String stk, String base, boolean zReady, boolean nReady) {
        return facts(AppEnvironment.DEVELOPMENT, false, stk, base, "auto", false, zReady, false, nReady);
    }

    @Test
    void neverOutsideDevelopment() {
        assertThat(DevTunnelPlanner.decide(facts(AppEnvironment.PRODUCTION, false, "", "http://localhost:8084", "auto", true, true, true, true)).action()).isEqualTo(Action.SKIP_ENV);
        assertThat(DevTunnelPlanner.decide(facts(AppEnvironment.STAGING, false, "", "http://localhost:8084", "auto", true, true, true, true)).action()).isEqualTo(Action.SKIP_ENV);
    }

    @Test
    void canBeSwitchedOff() {
        assertThat(DevTunnelPlanner.decide(facts(AppEnvironment.DEVELOPMENT, true, "", "http://localhost:8084", "auto", true, true, true, true)).action()).isEqualTo(Action.SKIP_OFF);
    }

    @Test
    void aConfiguredCallbackAddressIsLeftAlone() {
        assertThat(DevTunnelPlanner.decide(dev("https://mine.example.org", "http://localhost:8084", true, true)).action()).isEqualTo(Action.SKIP_CONFIGURED);
        assertThat(DevTunnelPlanner.decide(dev("   ", "http://localhost:8084", true, false)).action()).isEqualTo(Action.START);
    }

    @Test
    void aPublicBaseAddressIsEnough() {
        assertThat(DevTunnelPlanner.decide(dev("", "https://api.example.com", true, true)).action()).isEqualTo(Action.SKIP_PUBLIC);
    }

    @Test
    void aLocalOrPlainHttpBaseAddressIsNotEnough() {
        assertThat(DevTunnelPlanner.decide(dev("", "http://localhost:8084", true, false)).action()).isEqualTo(Action.START);
        assertThat(DevTunnelPlanner.decide(dev("", "https://localhost:8084", true, false)).action()).isEqualTo(Action.START);
        assertThat(DevTunnelPlanner.decide(dev("", "http://api.example.com", true, false)).action()).isEqualTo(Action.START);
        assertThat(DevTunnelPlanner.decide(dev("", "", true, false)).action()).isEqualTo(Action.START);
    }

    @Test
    void withoutAnyAccountItSaysWhatToAdd() {
        DevTunnelPlanner.Plan plan = DevTunnelPlanner.decide(dev("", "http://localhost:8084", false, false));
        assertThat(plan.action()).isEqualTo(Action.NO_CREDENTIALS);
        assertThat(plan.reason()).contains("ZROK_ENABLE_TOKEN").contains("NGROK_AUTHTOKEN");
    }

    @Test
    void zrokComesFirstWhenBothAreReadyAndNothingIsInstalled() {
        assertThat(DevTunnelPlanner.decide(dev("", "", true, true)).provider()).isEqualTo(Provider.ZROK);
        assertThat(DevTunnelPlanner.decide(dev("", "", true, false)).provider()).isEqualTo(Provider.ZROK);
        assertThat(DevTunnelPlanner.decide(dev("", "", false, true)).provider()).isEqualTo(Provider.NGROK);
    }

    @Test
    void anInstalledReadyProgramBeatsOneThatWouldHaveToBeDownloaded() {
        Facts f = facts(AppEnvironment.DEVELOPMENT, false, "", "", "auto", false, true, true, true);
        assertThat(DevTunnelPlanner.decide(f).provider()).isEqualTo(Provider.NGROK);
        Facts both = facts(AppEnvironment.DEVELOPMENT, false, "", "", "auto", true, true, true, true);
        assertThat(DevTunnelPlanner.decide(both).provider()).isEqualTo(Provider.ZROK);
    }

    @Test
    void theProviderCanBeForced() {
        assertThat(DevTunnelPlanner.decide(facts(AppEnvironment.DEVELOPMENT, false, "", "", "ngrok", true, true, false, true)).provider()).isEqualTo(Provider.NGROK);
        assertThat(DevTunnelPlanner.decide(facts(AppEnvironment.DEVELOPMENT, false, "", "", "zrok", false, false, true, true)).provider()).isEqualTo(Provider.ZROK);
        assertThat(DevTunnelPlanner.decide(facts(AppEnvironment.DEVELOPMENT, false, "", "", "ZROK2", false, false, true, true)).provider()).isEqualTo(Provider.ZROK);
    }
}
