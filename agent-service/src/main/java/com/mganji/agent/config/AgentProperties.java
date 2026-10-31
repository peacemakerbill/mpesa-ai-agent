package com.mganji.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Behaviour settings for the agent. See application.yml for what each one means.
 * businessPayments switches on the optional tools that use payment-service (STK Push for the business, refunds,
 * payouts, staff roles). Off by default: the agent then only prepares requests for the customer's own M-Pesa.
 */
@ConfigurationProperties(prefix = "agent")
public record AgentProperties(
        @DefaultValue("redis") String store,
        @DefaultValue("20") int historySize,
        @DefaultValue("PT24H") Duration historyTtl,
        @DefaultValue("12") int rateLimitPerMinute,
        @DefaultValue("6") int maxToolRounds,
        @DefaultValue("PT10M") Duration intentTtl,
        @DefaultValue("PT24H") Duration approvalIntentTtl,
        List<String> escalationPhones,
        @DefaultValue("5242880") long maxImageBytes,
        @DefaultValue("15728640") long maxDocumentBytes,
        @DefaultValue("16777216") long maxAudioBytes,
        @DefaultValue("20971520") long maxUploadBytes,
        @DefaultValue("20000") int maxTextChars,
        @DefaultValue("false") boolean businessPayments
) {
    public AgentProperties {
        escalationPhones = escalationPhones == null ? List.of() : escalationPhones.stream()
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
