package com.darajaagentx.api.service;

import com.darajaagentx.api.config.AppEnvironment;
import com.darajaagentx.api.config.DarajaProperties;
import com.darajaagentx.api.config.EnvironmentPolicy;
import com.darajaagentx.api.exception.DarajaRefusedException;
import com.darajaagentx.api.model.DarajaEnvironment;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A staging or development copy never reaches the live Safaricom system. Both refuse before anything is sent. */
class LiveDarajaGuardTest {

    // Nothing listens here, and the guard answers before any connection is tried.
    private static final DarajaProperties PROPS = new DarajaProperties("http://localhost:9", "http://localhost:9", "");

    @Test
    void aCallToProductionDarajaIsRefusedOutsideProduction() {
        for (AppEnvironment env : new AppEnvironment[]{AppEnvironment.STAGING, AppEnvironment.DEVELOPMENT}) {
            DarajaClient client = new DarajaClient(PROPS, TestSupport.clock(), new EnvironmentPolicy(env, false));
            assertThatThrownBy(() -> client.post(DarajaEnvironment.PRODUCTION, "key", "secret", "/mpesa/stkpush/v1/processrequest", Map.of()))
                    .isInstanceOf(DarajaRefusedException.class)
                    .hasMessageContaining(env.label())
                    .hasMessageContaining("no real money");
        }
    }

    @Test
    void theKeyCheckIsRefusedToo() {
        DarajaTokenProbe probe = new DarajaTokenProbe(PROPS, new EnvironmentPolicy(AppEnvironment.STAGING, false));
        assertThat(probe.probe(DarajaEnvironment.PRODUCTION, "key", "secret")).isEqualTo(new TokenProbe.Outcome(TokenProbe.Kind.BLOCKED, 0));
    }

    @Test
    void theSandboxIsStillReachedFromStaging() {
        // The sandbox address above is closed, so the key check reports that Safaricom could not be reached. It was not blocked.
        DarajaTokenProbe probe = new DarajaTokenProbe(PROPS, new EnvironmentPolicy(AppEnvironment.STAGING, false));
        assertThat(probe.probe(DarajaEnvironment.SANDBOX, "key", "secret").kind()).isNotEqualTo(TokenProbe.Kind.BLOCKED);
    }
}
