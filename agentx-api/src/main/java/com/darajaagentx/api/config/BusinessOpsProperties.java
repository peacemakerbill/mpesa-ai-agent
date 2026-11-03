package com.darajaagentx.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Limits for requests made on business accounts from the chat.
 *
 * @param maxAmount          the most one request may move, in whole KES
 * @param approvalTtl        how long a request waits for the owner's approval in the app
 * @param approveCollections true makes STK Push wait for approval in the app too. False sends it at once.
 * @param stkPerHour         STK Push requests one owner may make per hour, so a mistake or a trick cannot flood phones with prompts
 * @param maxOpenApprovals   requests waiting for approval at the same time
 */
@ConfigurationProperties(prefix = "agentx.business")
public record BusinessOpsProperties(
        @DefaultValue("150000") int maxAmount,
        @DefaultValue("PT30M") Duration approvalTtl,
        @DefaultValue("false") boolean approveCollections,
        @DefaultValue("20") int stkPerHour,
        @DefaultValue("10") int maxOpenApprovals
) {
}
