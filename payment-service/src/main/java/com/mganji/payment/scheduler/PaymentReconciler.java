package com.mganji.payment.scheduler;

import com.mganji.payment.config.ReconcileProperties;
import com.mganji.payment.dto.StkQueryResponse;
import com.mganji.payment.exception.MpesaApiException;
import com.mganji.payment.exception.MpesaCommunicationException;
import com.mganji.payment.model.Payment;
import com.mganji.payment.model.PaymentStatus;
import com.mganji.payment.repository.PaymentRepository;
import com.mganji.payment.service.MpesaClient;
import com.mganji.payment.service.PaymentStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Safety net for lost callbacks. Every minute it asks Daraja about payments that have been PUSHED for a
 * while without an answer, and gives up (TIMEOUT) after the configured time. It also warns about payments
 * stuck in PENDING, which need a manual look at the M-Pesa statement.
 */
@Component
@ConditionalOnProperty(prefix = "payments.reconcile", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentReconciler {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciler.class);

    private final PaymentRepository repository;
    private final MpesaClient mpesa;
    private final PaymentStateService state;
    private final ReconcileProperties props;

    public PaymentReconciler(PaymentRepository repository,
                             MpesaClient mpesa,
                             PaymentStateService state,
                             ReconcileProperties props) {
        this.repository = repository;
        this.mpesa = mpesa;
        this.state = state;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${payments.reconcile.interval:PT1M}")
    public void run() {
        reconcile(Instant.now());
    }

    public void reconcile(Instant now) {
        List<Payment> waiting = repository.findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                PaymentStatus.PUSHED, now.minus(props.minAge()));
        for (Payment payment : waiting) {
            try {
                reconcileOne(payment, now);
            } catch (RuntimeException e) {
                log.error("Reconciliation failed for payment {}", payment.getId(), e);
            }
        }
        warnAboutStuckPending(now);
    }

    private void reconcileOne(Payment payment, Instant now) {
        try {
            StkQueryResponse response = mpesa.stkQuery(payment.getCheckoutRequestId());
            if (response != null && response.hasFinalOutcome()) {
                state.applyResult(response.toResult(payment.getCheckoutRequestId()), "query");
                return;
            }
        } catch (MpesaApiException | MpesaCommunicationException e) {
            log.debug("Payment {} is still unresolved: {}", payment.getId(), e.getMessage());
        }
        if (payment.getCreatedAt().isBefore(now.minus(props.giveUpAfter()))) {
            state.markTimeout(payment.getId());
        }
    }

    private void warnAboutStuckPending(Instant now) {
        Instant from = now.minus(Duration.ofHours(24));
        Instant to = now.minus(props.giveUpAfter());
        for (Payment stuck : repository.findTop50ByStatusAndCreatedAtBetweenOrderByCreatedAtAsc(
                PaymentStatus.PENDING, from, to)) {
            log.warn("Payment {} has been PENDING since {}. Check the M-Pesa statement before retrying.",
                    stuck.getId(), stuck.getCreatedAt());
        }
    }
}
