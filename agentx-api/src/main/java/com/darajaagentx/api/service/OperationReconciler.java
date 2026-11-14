package com.darajaagentx.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Safety net for lost callbacks. Safaricom tells us the result of an STK Push by calling our address, and a call can be lost
 * (a restart, a network fault). Every minute this asks Safaricom about pushes that have waited too long, so a paid customer is
 * never left showing as unpaid. See {@link BusinessOperationService#reconcileStalledPushes}.
 * Settings (all optional): agentx.business.reconcile.enabled, interval, min-age, give-up-after.
 */
@Component
@ConditionalOnProperty(prefix = "agentx.business.reconcile", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OperationReconciler {

    private static final Logger log = LoggerFactory.getLogger(OperationReconciler.class);

    private final BusinessOperationService operations;
    private final Duration minAge;
    private final Duration giveUpAfter;

    public OperationReconciler(BusinessOperationService operations,
                               @Value("${agentx.business.reconcile.min-age:PT2M}") Duration minAge,
                               @Value("${agentx.business.reconcile.give-up-after:PT15M}") Duration giveUpAfter) {
        this.operations = operations;
        this.minAge = minAge;
        this.giveUpAfter = giveUpAfter;
    }

    @Scheduled(fixedDelayString = "${agentx.business.reconcile.interval:PT1M}", initialDelayString = "${agentx.business.reconcile.interval:PT1M}")
    public void run() {
        try {
            int changed = operations.reconcileStalledPushes(minAge, giveUpAfter);
            if (changed > 0) {
                log.info("Recovered {} STK Push request(s) whose callback never arrived", changed);
            }
        } catch (RuntimeException e) {
            log.warn("The check for lost STK Push answers failed: {}", e.getMessage());
        }
    }
}
