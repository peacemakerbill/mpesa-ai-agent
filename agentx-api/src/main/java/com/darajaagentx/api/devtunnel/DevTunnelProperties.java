package com.darajaagentx.api.devtunnel;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The automatic tunnel for STK Push answers, used on a developer's machine only (see docs/DEV_TUNNEL.md).
 *
 * @param mode               auto (default) opens a tunnel when none is configured, off never does
 * @param provider           auto (zrok2 first), zrok or ngrok
 * @param install            true lets it download zrok2 or ngrok into the user's own folder when it is missing
 * @param waitSeconds        how long to wait for the tunnel's address
 * @param zrokToken          zrok account token, used once to enable this machine
 * @param ngrokToken         ngrok authtoken
 * @param zrokVersion        a zrok2 version to download, for example v2.0.0. Blank means the latest
 * @param zrokShareArgs      extra arguments for "zrok2 share public"
 * @param zrokDownloadUrl    a complete download address that replaces the usual one
 * @param ngrokDownloadUrl   a complete download address that replaces the usual one
 * @param binDir             where downloaded programs go. Blank means .agentx/bin in the user's home folder
 * @param ngrokApi           the address of ngrok's own local API
 */
@ConfigurationProperties(prefix = "agentx.dev-tunnel")
public record DevTunnelProperties(
        @DefaultValue("auto") String mode,
        @DefaultValue("auto") String provider,
        @DefaultValue("true") boolean install,
        @DefaultValue("40") int waitSeconds,
        @DefaultValue("") String zrokToken,
        @DefaultValue("") String ngrokToken,
        @DefaultValue("") String zrokVersion,
        @DefaultValue("") String zrokShareArgs,
        @DefaultValue("") String zrokDownloadUrl,
        @DefaultValue("") String ngrokDownloadUrl,
        @DefaultValue("") String binDir,
        @DefaultValue("http://127.0.0.1:4040") String ngrokApi
) {
    public DevTunnelProperties {
        mode = mode == null ? "auto" : mode.strip();
        provider = provider == null ? "auto" : provider.strip();
        zrokToken = blankIfNull(zrokToken);
        ngrokToken = blankIfNull(ngrokToken);
        zrokVersion = blankIfNull(zrokVersion);
        zrokShareArgs = blankIfNull(zrokShareArgs);
        zrokDownloadUrl = blankIfNull(zrokDownloadUrl);
        ngrokDownloadUrl = blankIfNull(ngrokDownloadUrl);
        binDir = blankIfNull(binDir);
        ngrokApi = ngrokApi == null || ngrokApi.isBlank() ? "http://127.0.0.1:4040" : ngrokApi.strip();
        waitSeconds = waitSeconds <= 0 ? 40 : waitSeconds;
    }

    public boolean off() {
        String m = mode.toLowerCase(java.util.Locale.ROOT);
        return m.equals("off") || m.equals("false") || m.equals("0") || m.equals("no");
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value.strip();
    }
}
