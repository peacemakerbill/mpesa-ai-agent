package com.darajaagentx.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Sign up and profile settings.
 *
 * @param signupEnabled   false closes sign up. People who already have an account can still sign in.
 * @param signupTtl       how long a started sign up waits for its SMS code
 * @param maxRecipients   saved email recipients one customer may keep
 */
@ConfigurationProperties(prefix = "agentx.profile")
public record ProfileProperties(
        @DefaultValue("true") boolean signupEnabled,
        @DefaultValue("PT15M") Duration signupTtl,
        @DefaultValue("200") int maxRecipients
) {
}
