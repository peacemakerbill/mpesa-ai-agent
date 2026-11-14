package com.darajaagentx.api.config;

import com.darajaagentx.api.model.DarajaEnvironment;

/**
 * What this copy of the system is allowed to do, from APP_ENV.
 *
 * @param env             the environment this copy runs as
 * @param allowLiveDaraja lets a copy that is not production call Safaricom's production Daraja. Off by default, so a
 *                        staging copy pointed at a real business account cannot move real money.
 */
public record EnvironmentPolicy(AppEnvironment env, boolean allowLiveDaraja) {

    public EnvironmentPolicy {
        env = env == null ? AppEnvironment.DEVELOPMENT : env;
    }

    /** The policy of the live system: nothing is held back. */
    public static EnvironmentPolicy production() {
        return new EnvironmentPolicy(AppEnvironment.PRODUCTION, false);
    }

    /** True when a call to this Daraja would reach the live Safaricom system from a copy that must not. */
    public boolean liveDarajaBlocked(DarajaEnvironment daraja) {
        return daraja == DarajaEnvironment.PRODUCTION && !env.isProduction() && !allowLiveDaraja;
    }

    /** Safe to show to a person. */
    public String liveDarajaBlockedMessage() {
        return "Live Daraja is switched off in the " + env.label() + " environment, so no real money can move here. "
                + "Use a sandbox business account, or run the production system.";
    }
}
