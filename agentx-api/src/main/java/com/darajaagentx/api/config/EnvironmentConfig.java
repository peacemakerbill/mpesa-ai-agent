package com.darajaagentx.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Reads APP_ENV (agentx.env) once, and refuses to start on a value it does not know. */
@Configuration(proxyBeanMethods = false)
public class EnvironmentConfig {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentConfig.class);

    @Bean
    EnvironmentPolicy environmentPolicy(@Value("${agentx.env:development}") String env,
                                        @Value("${agentx.allow-live-daraja:false}") boolean allowLiveDaraja) {
        return create(env, allowLiveDaraja);
    }

    static EnvironmentPolicy create(String env, boolean allowLiveDaraja) {
        AppEnvironment parsed = AppEnvironment.parse(env);
        if (parsed == null) {
            throw new IllegalStateException("APP_ENV (agentx.env) must be development, staging or production, not \"" + env + "\".");
        }
        log.info("Environment: {}", parsed.label());
        return new EnvironmentPolicy(parsed, allowLiveDaraja);
    }
}
