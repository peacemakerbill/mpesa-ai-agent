package com.darajaagentx.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Checks the settings against the environment when the service starts. In production a setting that is only meant for
 * development stops the service, because starting anyway would put customers at risk. In staging the same problems are
 * only written to the log, so a test copy can be set up step by step. In development nothing is checked.
 */
@Component
public class EnvironmentGuard implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentGuard.class);

    /** The few settings the checks look at, taken from the real configuration. */
    public record Settings(String codeDelivery, String corsOrigins, String internalKey, String tokenSecret,
                           String publicBaseUrl, String mailTransport, String vaultKey, String storageType,
                           boolean allowLiveDaraja, String showCodeInApp) {

        /** The same settings with no opinion on whether codes are shown on screen (auto). */
        public Settings(String codeDelivery, String corsOrigins, String internalKey, String tokenSecret,
                        String publicBaseUrl, String mailTransport, String vaultKey, String storageType,
                        boolean allowLiveDaraja) {
            this(codeDelivery, corsOrigins, internalKey, tokenSecret, publicBaseUrl, mailTransport, vaultKey, storageType,
                    allowLiveDaraja, "auto");
        }
    }

    /**
     * @param blockers things that must be fixed before this can run as production
     * @param notes    things worth knowing, which do not stop anything
     */
    public record Findings(List<String> blockers, List<String> notes) {
        public Findings {
            blockers = List.copyOf(blockers);
            notes = List.copyOf(notes);
        }
    }

    private final EnvironmentPolicy policy;
    private final Settings settings;

    public EnvironmentGuard(EnvironmentPolicy policy, AgentXProperties agentx, InternalProperties internal,
                            MailProperties mail, VaultProperties vault, StorageProperties storage,
                            @Value("${cors.allowed-origins:}") String cors,
                            @Value("${agentx.show-code-in-app:auto}") String showCodeInApp) {
        this.policy = policy;
        this.settings = new Settings(agentx.codeDelivery(), cors, internal.key(), agentx.tokenSecret(), agentx.publicBaseUrl(),
                mail.transport(), vault.key(), storage.type(), policy.allowLiveDaraja(), showCodeInApp);
    }

    @Override
    public void afterPropertiesSet() {
        enforce(policy.env(), check(policy.env(), settings));
    }

    static void enforce(AppEnvironment env, Findings findings) {
        switch (env) {
            case PRODUCTION -> {
                findings.notes().forEach(n -> log.warn("Production: {}", n));
                if (!findings.blockers().isEmpty()) {
                    throw new IllegalStateException("This cannot start as production (APP_ENV=production):\n - "
                            + String.join("\n - ", findings.blockers())
                            + "\nFix these settings, or set APP_ENV to staging or development.");
                }
            }
            case STAGING -> {
                findings.blockers().forEach(b -> log.warn("Staging, and production would refuse to start: {}", b));
                findings.notes().forEach(n -> log.warn("Staging: {}", n));
            }
            case DEVELOPMENT -> {
                // Nothing to check on a developer's machine.
            }
        }
    }

    /** Pure, so every rule can be tested without starting the application. */
    public static Findings check(AppEnvironment env, Settings s) {
        List<String> blockers = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        if (env == AppEnvironment.DEVELOPMENT) {
            return new Findings(blockers, notes);
        }

        CodeVisibility visibility = CodeVisibility.parse(s.showCodeInApp());
        if ("dev".equalsIgnoreCase(trim(s.codeDelivery()))) {
            blockers.add("CODE_DELIVERY=dev hands every sign-in code back to whoever asks. Use sms.");
        } else if ("log".equalsIgnoreCase(trim(s.codeDelivery())) || trim(s.codeDelivery()).isEmpty()) {
            notes.add("CODE_DELIVERY=log writes sign-in codes to the log, so customers do not receive them. Use sms.");
        }
        if (visibility == CodeVisibility.ON) {
            blockers.add("CODE_SHOW_IN_APP=true shows every sign-in code on screen, so anyone who knows a phone number can sign in as it. "
                    + "Set it to false (or leave it as auto) and use sms. Production never shows a code even then.");
        } else if (env == AppEnvironment.STAGING && visibility == CodeVisibility.AUTO) {
            notes.add("Sign-in codes are shown in the app, because CODE_SHOW_IN_APP is on auto. Anyone who knows a phone number can sign in as it. "
                    + "Set CODE_SHOW_IN_APP=false once CODE_DELIVERY=sms works.");
        }
        if (!trim(s.corsOrigins()).isEmpty()) {
            blockers.add("CORS_ALLOWED_ORIGINS is set. It opens the API to a web page and is only for the test console. Leave it blank.");
        }
        if (weak(s.internalKey())) {
            blockers.add("INTERNAL_KEY is missing, short or still the example value. Make one with: openssl rand -base64 32");
        }
        if (weak(s.tokenSecret())) {
            blockers.add("AGENTX_TOKEN_SECRET still looks like an example value. Make one with: openssl rand -base64 48");
        }
        String url = trim(s.publicBaseUrl());
        if (!publicHttps(url)) {
            blockers.add("AGENTX_PUBLIC_BASE_URL must be the public https address of this service, not \"" + url + "\".");
        }
        if ("log".equalsIgnoreCase(trim(s.mailTransport())) || trim(s.mailTransport()).isEmpty()) {
            notes.add("MAIL_TRANSPORT=log means no email leaves this service. Use smtp.");
        }
        if (trim(s.vaultKey()).isEmpty()) {
            notes.add("AGENTX_VAULT_KEY is blank, so business accounts cannot be saved.");
        }
        if ("local".equalsIgnoreCase(trim(s.storageType()))) {
            notes.add("STORAGE_TYPE=local keeps files on this machine's disk, which many hosts wipe on every deploy. Use database or cloudinary.");
        }
        if (s.allowLiveDaraja() && env != AppEnvironment.PRODUCTION) {
            notes.add("AGENTX_ALLOW_LIVE_DARAJA is on, so this " + env.label() + " copy can move real money through a production Daraja account.");
        }
        return new Findings(blockers, notes);
    }

    private static boolean weak(String value) {
        String v = trim(value).toLowerCase(Locale.ROOT);
        if (v.length() < 24) {
            return true;
        }
        for (String marker : new String[]{"change-me", "changeme", "change_me", "replace-me", "replaceme", "example", "your-", "your_"}) {
            if (v.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static boolean publicHttps(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || host.isBlank()) {
                return false;
            }
            String h = host.toLowerCase(Locale.ROOT);
            return !(h.equals("localhost") || h.equals("0.0.0.0") || h.startsWith("127.") || h.equals("[::1]") || h.endsWith(".local"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
