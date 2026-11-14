package com.darajaagentx.api.devtunnel;

import com.darajaagentx.api.config.AppEnvironment;
import com.darajaagentx.api.service.StkCallback;

import java.util.Locale;

/** Decides whether a tunnel is needed, and which one. It only reads facts it is given, so every case can be tested. */
public final class DevTunnelPlanner {

    public enum Action { SKIP_ENV, SKIP_OFF, SKIP_CONFIGURED, SKIP_PUBLIC, NO_CREDENTIALS, START }

    public record Plan(Action action, Provider provider, String reason) {
    }

    /**
     * @param env              the copy of the system this is (APP_ENV)
     * @param off              true when DEV_TUNNEL is off
     * @param stkCallbackUrl   AGENTX_STK_CALLBACK_URL, blank when not set
     * @param publicBaseUrl    AGENTX_PUBLIC_BASE_URL
     * @param providerSetting  auto, zrok or ngrok
     * @param zrokInstalled    zrok2 is on the PATH or in the tool folder
     * @param zrokReady        this machine is enabled for zrok, or a token is available
     * @param ngrokInstalled   ngrok is on the PATH or in the tool folder
     * @param ngrokReady       ngrok has an authtoken, or one is available
     */
    public record Facts(AppEnvironment env, boolean off, String stkCallbackUrl, String publicBaseUrl, String providerSetting,
                        boolean zrokInstalled, boolean zrokReady, boolean ngrokInstalled, boolean ngrokReady) {
    }

    private DevTunnelPlanner() {
    }

    public static Plan decide(Facts f) {
        if (f.env() != AppEnvironment.DEVELOPMENT) {
            return new Plan(Action.SKIP_ENV, null, "APP_ENV is " + (f.env() == null ? "unknown" : f.env().label()) + " and the tunnel is for development only");
        }
        if (f.off()) {
            return new Plan(Action.SKIP_OFF, null, "DEV_TUNNEL is off");
        }
        String stk = StkCallback.normalize(f.stkCallbackUrl());
        if (!stk.isEmpty()) {
            return new Plan(Action.SKIP_CONFIGURED, null, "AGENTX_STK_CALLBACK_URL is already set to " + stk);
        }
        String base = StkCallback.normalize(f.publicBaseUrl());
        if (base.startsWith("https://") && StkCallback.problem(base) == null) {
            return new Plan(Action.SKIP_PUBLIC, null, "AGENTX_PUBLIC_BASE_URL (" + base + ") is already a public https address, so STK answers use it");
        }
        Provider chosen = choose(f);
        if (chosen == null) {
            return new Plan(Action.NO_CREDENTIALS, null, "no zrok or ngrok account token is set, so no tunnel was opened. "
                    + "Set ZROK_ENABLE_TOKEN or NGROK_AUTHTOKEN in .env (it installs the program itself), or AGENTX_STK_CALLBACK_URL. "
                    + "Until then the prompt still reaches the phone and the result is fetched from Safaricom about every minute");
        }
        return new Plan(Action.START, chosen, "no public address is configured, so a " + chosen.program() + " tunnel is opened");
    }

    private static Provider choose(Facts f) {
        String want = f.providerSetting() == null ? "auto" : f.providerSetting().toLowerCase(Locale.ROOT).strip();
        switch (want) {
            case "zrok", "zrok2" -> {
                return Provider.ZROK;
            }
            case "ngrok" -> {
                return Provider.NGROK;
            }
            default -> {
            }
        }
        if (f.zrokInstalled() && f.zrokReady()) {
            return Provider.ZROK;
        }
        if (f.ngrokInstalled() && f.ngrokReady()) {
            return Provider.NGROK;
        }
        if (f.zrokReady()) {
            return Provider.ZROK;
        }
        if (f.ngrokReady()) {
            return Provider.NGROK;
        }
        return null;
    }
}
