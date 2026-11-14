package com.darajaagentx.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads APP_ENV (agentx.env), the same setting agentx-api uses, and checks it against this service's settings.
 * development is not checked. staging only writes warnings. production refuses to start with a setting that is only
 * meant for development, because starting anyway would put customers at risk.
 */
@Component
public class EnvironmentGuard implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentGuard.class);

    public enum Env {
        DEVELOPMENT, STAGING, PRODUCTION;

        /** development, dev, local, staging, stage, uat, qa, test, production, prod, live. Blank is development. Null when unknown. */
        public static Env parse(String text) {
            if (text == null || text.isBlank()) {
                return DEVELOPMENT;
            }
            return switch (text.trim().toLowerCase(Locale.ROOT)) {
                case "development", "dev", "local" -> DEVELOPMENT;
                case "staging", "stage", "uat", "qa", "test" -> STAGING;
                case "production", "prod", "live" -> PRODUCTION;
                default -> null;
            };
        }

        public String label() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final String rawEnv;
    private final String store;
    private final String cors;
    private final String internalKey;
    private final String publicUrl;

    public EnvironmentGuard(@Value("${agentx.env:development}") String rawEnv,
                            @Value("${agent.store:redis}") String store,
                            @Value("${cors.allowed-origins:}") String cors,
                            @Value("${internal.key:}") String internalKey,
                            @Value("${agentx.public-url:}") String publicUrl) {
        this.rawEnv = rawEnv;
        this.store = store;
        this.cors = cors;
        this.internalKey = internalKey;
        this.publicUrl = publicUrl;
    }

    @Override
    public void afterPropertiesSet() {
        Env env = Env.parse(rawEnv);
        if (env == null) {
            throw new IllegalStateException("APP_ENV (agentx.env) must be development, staging or production, not \"" + rawEnv + "\".");
        }
        log.info("Environment: {}", env.label());
        List<String> blockers = blockers(env, store, cors, internalKey, publicUrl);
        if (env == Env.PRODUCTION && !blockers.isEmpty()) {
            throw new IllegalStateException("This cannot start as production (APP_ENV=production):\n - "
                    + String.join("\n - ", blockers) + "\nFix these settings, or set APP_ENV to staging or development.");
        }
        if (env == Env.STAGING) {
            blockers.forEach(b -> log.warn("Staging, and production would refuse to start: {}", b));
        }
    }

    /** Pure, so every rule can be tested without starting the application. Development has none. */
    public static List<String> blockers(Env env, String store, String cors, String internalKey, String publicUrl) {
        List<String> out = new ArrayList<>();
        if (env == Env.DEVELOPMENT) {
            return out;
        }
        if ("memory".equalsIgnoreCase(trim(store))) {
            out.add("AGENT_STORE=memory keeps every chat in this process only, so a restart wipes them. Use redis.");
        }
        if (!trim(cors).isEmpty()) {
            out.add("CORS_ALLOWED_ORIGINS is set. It is only for the test console. Leave it blank.");
        }
        String key = trim(internalKey).toLowerCase(Locale.ROOT);
        if (key.length() < 24 || key.contains("change-me") || key.contains("changeme") || key.contains("example")) {
            out.add("INTERNAL_KEY is missing, short or still the example value. It must match agentx-api. Make one with: openssl rand -base64 32");
        }
        String url = trim(publicUrl).toLowerCase(Locale.ROOT);
        if (!url.startsWith("https://") || url.startsWith("https://localhost") || url.startsWith("https://127.")) {
            out.add("AGENTX_PUBLIC_BASE_URL must be the public https address of agentx-api, not \"" + trim(publicUrl) + "\".");
        }
        return out;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
