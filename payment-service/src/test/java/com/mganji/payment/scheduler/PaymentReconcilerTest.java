package com.mganji.payment.scheduler;

import com.mganji.payment.config.ReconcileProperties;
import com.mganji.payment.dto.StkQueryResponse;
import com.mganji.payment.dto.StkResult;
import com.mganji.payment.exception.MpesaApiException;
import com.mganji.payment.model.Payment;
import com.mganji.payment.model.PaymentStatus;
import com.mganji.payment.repository.PaymentRepository;
import com.mganji.payment.service.MpesaClient;
import com.mganji.payment.service.PaymentStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentReconcilerTest {

    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

    @Mock PaymentRepository repository;
    @Mock MpesaClient mpesa;
    @Mock PaymentStateService state;

    private PaymentReconciler reconciler;

    @BeforeEach
    void setUp() {
        ReconcileProperties props = new ReconcileProperties(
                true, Duration.ofMinutes(1), Duration.ofMinutes(2), Duration.ofMinutes(15));
        reconciler = new PaymentReconciler(repository, mpesa, state, props);
    }

    private Payment waitingPayment(Duration age) {
        Payment p = new Payment("k1", "254712345678", 100, "ORD-1");
        ReflectionTestUtils.setField(p, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(p, "createdAt", NOW.minus(age));
        p.setStatus(PaymentStatus.PUSHED);
        p.setCheckoutRequestId("ws_CO_1");
        when(repository.findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(eq(PaymentStatus.PUSHED), any(Instant.class)))
                .thenReturn(List.of(p));
        return p;
    }

    @Test
    void appliesTheOutcomeWhenDarajaAlreadyKnowsIt() {
        waitingPayment(Duration.ofMinutes(3));
        when(mpesa.stkQuery("ws_CO_1")).thenReturn(
                new StkQueryResponse("m1", "ws_CO_1", "0", "Accepted", "0", "The service request is processed successfully."));

        reconciler.reconcile(NOW);

        verify(state).applyResult(
                new StkResult("ws_CO_1", "0", "The service request is processed successfully.", null, null), "query");
        verify(state, never()).markTimeout(any());
    }

    @Test
    void leavesRecentPaymentsAloneWhileDarajaSaysStillProcessing() {
        waitingPayment(Duration.ofMinutes(3));
        when(mpesa.stkQuery("ws_CO_1")).thenThrow(new MpesaApiException("500.001.1001", "The transaction is being processed"));

        reconciler.reconcile(NOW);

        verifyNoInteractions(state);
    }

    @Test
    void timesOutPaymentsThatNeverGotAnAnswer() {
        Payment old = waitingPayment(Duration.ofMinutes(20));
        when(mpesa.stkQuery("ws_CO_1")).thenThrow(new MpesaApiException("500.001.1001", "The transaction is being processed"));

        reconciler.reconcile(NOW);

        verify(state).markTimeout(old.getId());
    }
}
