package com.mpesaagent.payment.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Spending limits in whole KES, enforced in code and never left to the AI. */
@Validated
@ConfigurationProperties(prefix = "payments.limits")
public record LimitsProperties(
        @Min(1) int perTransaction,
        @Min(1) long perDay
) {
}
