package com.mpesaagent.payment.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "payments.reconcile")
public record ReconcileProperties(
        boolean enabled,
        @NotNull Duration interval,
        /* how long to wait for a callback before asking Daraja directly */
        @NotNull Duration minAge,
        /* after this long without an answer a PUSHED payment becomes TIMEOUT */
        @NotNull Duration giveUpAfter
) {
}
