package com.mganji.agent.service;

import com.mganji.agent.TestSupport;
import com.mganji.agent.client.PaymentClient;
import com.mganji.agent.dto.OperationView;
import com.mganji.agent.dto.PaymentView;
import com.mganji.agent.exception.PaymentApiException;
import com.mganji.agent.model.Intent;
import com.mganji.agent.model.IntentType;
import com.mganji.agent.store.InMemoryStateStore;
import com.mganji.agent.store.IntentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ButtonHandlerTest {

    private static final String CUSTOMER = "254700000001";
    private static final String OTHER = "254700000002";
    private static final String APPROVER = "254711000002";
    private static final UUID OP_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID PAYMENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock PaymentClient payments;
    @Mock ReplySender sender;
    @Mock com.mganji.agent.client.GanjiClient ganji;

    private IntentStore intents;
    private ButtonHandler handler;

    @BeforeEach
    void setUp() {
        intents = new IntentStore(new InMemoryStateStore(), TestSupport.mapper());
        handler = new ButtonHandler(intents, payments, sender, ganji);
    }

    private Intent paymentIntent() {
        return intents.create(IntentType.PAYMENT, CUSTOMER, Map.of("amount", "500", "reference", "ORD-1"), Duration.ofMinutes(10));
    }

    private static PaymentView payment(String status) {
        return new PaymentView(PAYMENT_ID, status, CUSTOMER, 500, "ORD-1", "ws_CO_1", null, null, null, Instant.now(), Instant.now());
    }

    @Test
    void recognisesOnlyItsOwnButtonIds() {
        assertThat(ButtonHandler.handles("PAY:abc")).isTrue();
        assertThat(ButtonHandler.handles("APPROVE:abc")).isTrue();
        assertThat(ButtonHandler.handles("RCPT:abc")).isTrue();
        assertThat(ButtonHandler.handles("MENU:pay")).isFalse();
        assertThat(ButtonHandler.handles("PAY:")).isFalse();
        assertThat(ButtonHandler.handles(null)).isFalse();
    }

    @Test
    void tappingPayStartsExactlyOnePaymentWithTheIntentIdAsIdempotencyKey() {
        Intent intent = paymentIntent();
        when(payments.createPayment(anyString(), eq(CUSTOMER), eq(500), eq("ORD-1"))).thenReturn(payment("PUSHED"));

        handler.handle(CUSTOMER, "PAY:" + intent.id());

        verify(payments).createPayment("wa-" + intent.id(), CUSTOMER, 500, "ORD-1");
        verify(sender).text(eq(CUSTOMER), contains("M-Pesa prompt"));
    }

    @Test
    void doubleTapDoesNotChargeTwice() {
        Intent intent = paymentIntent();
        when(payments.createPayment(anyString(), anyString(), anyInt(), anyString())).thenReturn(payment("PUSHED"));

        handler.handle(CUSTOMER, "PAY:" + intent.id());
        handler.handle(CUSTOMER, "PAY:" + intent.id());

        verify(payments, times(1)).createPayment(anyString(), anyString(), anyInt(), anyString());
        verify(sender).text(eq(CUSTOMER), contains("expired or was already used"));
    }

    @Test
    void aTapFromAnotherNumberDoesNothing() {
        Intent intent = paymentIntent();

        handler.handle(OTHER, "PAY:" + intent.id());

        verifyNoInteractions(payments);
        verify(sender).text(eq(OTHER), contains("expired or was already used"));
        // and the rightful owner can still use it
        when(payments.createPayment(anyString(), anyString(), anyInt(), anyString())).thenReturn(payment("PUSHED"));
        handler.handle(CUSTOMER, "PAY:" + intent.id());
        verify(payments).createPayment(anyString(), eq(CUSTOMER), anyInt(), anyString());
    }

    @Test
    void aPaymentIntentCannotBeUsedAsARefundConfirmation() {
        Intent intent = paymentIntent();

        handler.handle(CUSTOMER, "OK:" + intent.id());

        verify(payments, never()).createOperation(any());
        verify(payments, never()).createPayment(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void cancelRemovesTheIntentSoPayCannotFollow() {
        Intent intent = paymentIntent();

        handler.handle(CUSTOMER, "NO:" + intent.id());
        handler.handle(CUSTOMER, "PAY:" + intent.id());

        verify(sender).text(eq(CUSTOMER), contains("cancelled"));
        verify(payments, never()).createPayment(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void safaricomRefusalIsExplainedAndNothingIsClaimedAsSent() {
        Intent intent = paymentIntent();
        PaymentView failed = new PaymentView(PAYMENT_ID, "FAILED", CUSTOMER, 500, "ORD-1", null, null, "1", "Invalid initiator",
                Instant.now(), Instant.now());
        when(payments.createPayment(anyString(), anyString(), anyInt(), anyString())).thenReturn(failed);

        handler.handle(CUSTOMER, "PAY:" + intent.id());

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(sender).text(eq(CUSTOMER), text.capture());
        assertThat(text.getValue()).contains("not been charged").contains("Invalid initiator").doesNotContain("I've sent");
    }

    @Test
    void aTimeoutTalkingToPaymentServiceNeverSaysNothingWasCharged() {
        Intent intent = paymentIntent();
        when(payments.createPayment(anyString(), anyString(), anyInt(), anyString()))
                .thenThrow(new PaymentApiException(0, "The payment service is not reachable right now"));

        handler.handle(CUSTOMER, "PAY:" + intent.id());

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(sender).text(eq(CUSTOMER), text.capture());
        assertThat(text.getValue()).contains("could not confirm").contains("Don't pay again yet").doesNotContain("not been charged");
    }

    @Test
    void aLimitRefusalIsShownAsIs() {
        Intent intent = paymentIntent();
        when(payments.createPayment(anyString(), anyString(), anyInt(), anyString()))
                .thenThrow(new PaymentApiException(422, "Amount is above the per-transaction limit of KES 70000"));

        handler.handle(CUSTOMER, "PAY:" + intent.id());

        verify(sender).text(eq(CUSTOMER), contains("per-transaction limit"));
    }

    @Test
    void refundConfirmationSubmitsTheRequestWithServerSideDetails() {
        Intent intent = intents.create(IntentType.REFUND, CUSTOMER,
                Map.of("paymentId", PAYMENT_ID.toString(), "amount", "300", "reason", "wrong item"), Duration.ofMinutes(10));
        when(payments.createOperation(any())).thenReturn(new OperationView(OP_ID, "REFUND", "PENDING_APPROVAL", CUSTOMER, null,
                CUSTOMER, 300, null, PAYMENT_ID, "wrong item", null, null, null, null, Instant.now(), Instant.now()));

        handler.handle(CUSTOMER, "OK:" + intent.id());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
        verify(payments).createOperation(body.capture());
        assertThat(body.getValue()).containsEntry("type", "REFUND").containsEntry("amount", 300)
                .containsEntry("relatedPaymentId", PAYMENT_ID.toString()).containsEntry("actorPhone", CUSTOMER)
                .containsEntry("idempotencyKey", "wa-" + intent.id());
        verify(sender).text(eq(CUSTOMER), contains("submitted for review"));
    }

    @Test
    void approverTapApprovesOnlyThroughTheirOwnIntent() {
        Intent intent = intents.create(IntentType.APPROVAL, APPROVER, Map.of("operationId", OP_ID.toString()), Duration.ofHours(1));

        handler.handle(CUSTOMER, "APPROVE:" + intent.id());          // a customer cannot use an approver's button
        verify(payments, never()).approve(any(), anyString());

        handler.handle(APPROVER, "APPROVE:" + intent.id());
        verify(payments).approve(OP_ID, APPROVER);
        verify(sender).text(eq(APPROVER), contains("Approved"));
    }

    @Test
    void rejectingCallsRejectAndTellsTheApprover() {
        Intent intent = intents.create(IntentType.APPROVAL, APPROVER, Map.of("operationId", OP_ID.toString()), Duration.ofHours(1));

        handler.handle(APPROVER, "REJECT:" + intent.id());

        verify(payments).reject(eq(OP_ID), eq(APPROVER), anyString());
        verify(payments, never()).approve(any(), anyString());
    }

    @Test
    void paymentServiceRefusingAnApprovalIsReportedToTheApprover() {
        Intent intent = intents.create(IntentType.APPROVAL, APPROVER, Map.of("operationId", OP_ID.toString()), Duration.ofHours(1));
        when(payments.approve(OP_ID, APPROVER)).thenThrow(new PaymentApiException(403, "You cannot approve a request you made yourself"));

        handler.handle(APPROVER, "APPROVE:" + intent.id());

        verify(sender).text(eq(APPROVER), contains("do not have permission"));
    }

    @Test
    void receiptButtonAsksPaymentServiceWithTheTappersPhone() {
        when(payments.receipt(PAYMENT_ID, CUSTOMER)).thenReturn(new byte[]{'%', 'P', 'D', 'F'});

        handler.handle(CUSTOMER, "RCPT:" + PAYMENT_ID);

        verify(payments).receipt(PAYMENT_ID, CUSTOMER);
        verify(sender).document(eq(CUSTOMER), any(), eq("application/pdf"), anyString(), anyString());
    }

    @Test
    void receiptForSomeoneElsesPaymentIsRefusedByPaymentService() {
        when(payments.receipt(PAYMENT_ID, OTHER)).thenThrow(new PaymentApiException(404, "Payment not found"));

        handler.handle(OTHER, "RCPT:" + PAYMENT_ID);

        verify(sender).text(eq(OTHER), contains("couldn't find"));
        verify(sender, never()).document(anyString(), any(), anyString(), anyString(), anyString());
    }

    @Test
    void recognisesTheWalletCancelButton() {
        assertThat(ButtonHandler.handles("WCAN:" + OP_ID)).isTrue();
    }

    private static com.mganji.agent.dto.WalletActionView walletAction() {
        return new com.mganji.agent.dto.WalletActionView(OP_ID, "PAY_TILL", "CANCELLED", 300, null, "TEXT",
                "Pay KES 300 to Till 123456", List.of(), false, null, null, Instant.now(), Instant.now());
    }

    @Test
    void cancellingAWalletRequestUsesTheTappersOwnNumber() {
        when(ganji.cancel(OP_ID, CUSTOMER)).thenReturn(walletAction());

        handler.handle(CUSTOMER, "WCAN:" + OP_ID);

        verify(ganji).cancel(OP_ID, CUSTOMER);
        verify(sender).text(eq(CUSTOMER), contains("Nothing was sent"));
    }

    @Test
    void cancellingSomeoneElsesWalletRequestIsRefusedByGanjiApi() {
        when(ganji.cancel(OP_ID, OTHER)).thenThrow(new com.mganji.agent.exception.GanjiApiException(404, "Request not found"));

        handler.handle(OTHER, "WCAN:" + OP_ID);

        verify(sender).text(eq(OTHER), contains("couldn't find"));
    }

    @Test
    void aRequestAlreadyTakenToMPesaCannotBeCancelled() {
        when(ganji.cancel(OP_ID, CUSTOMER)).thenThrow(new com.mganji.agent.exception.GanjiApiException(409, "no"));

        handler.handle(CUSTOMER, "WCAN:" + OP_ID);

        verify(sender).text(eq(CUSTOMER), contains("check your M-Pesa messages"));
    }

    @Test
    void aBrokenWalletRequestIdDoesNotCallGanjiApi() {
        handler.handle(CUSTOMER, "WCAN:not-a-uuid");

        verify(ganji, never()).cancel(any(), anyString());
        verify(sender).text(eq(CUSTOMER), contains("expired or was already used"));
    }
}
