package com.mpesaagent.payment.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Limits for money leaving the business account, in whole KES. */
@Validated
@ConfigurationProperties(prefix = "operations")
public record OperationsProperties(
        @DefaultValue("20000") @Min(1) int maxPayout,
        @DefaultValue("100000") @Min(1) long dailyPayoutLimit,
        /* false means the person who asked can never approve their own request */
        @DefaultValue("false") boolean allowSelfApproval
) {
}
