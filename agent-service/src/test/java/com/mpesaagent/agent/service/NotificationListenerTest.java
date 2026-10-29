package com.mpesaagent.agent.service;

import com.mpesaagent.agent.TestSupport;
import com.mpesaagent.agent.chat.ChatChannel.Button;
import com.mpesaagent.agent.dto.ApprovalRequestedEvent;
import com.mpesaagent.agent.dto.C2bReceivedEvent;
import com.mpesaagent.agent.dto.OperationDoneEvent;
import com.mpesaagent.agent.dto.PaymentDoneEvent;
import com.mpesaagent.agent.dto.WalletActionEvent;
import com.mpesaagent.agent.model.Identity;
import com.mpesaagent.agent.model.IntentType;
import com.mpesaagent.agent.model.Role;
import com.mpesaagent.agent.store.DedupeStore;
import com.mpesaagent.agent.store.InMemoryStateStore;
import com.mpesaagent.agent.store.IntentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationListenerTest {

    private static final String CUSTOMER = "254700000001";
    private static final String APPROVER_A = "254711000002";
    private static final String APPROVER_B = "254711000004";
    private static final String TEAM = "254711000003";

    @Mock ReplySender sender;
    @Mock RoleService roles;

    private IntentStore intents;
    private NotificationListener listener;

    @BeforeEach
    void setUp() {
        InMemoryStateStore store = new InMemoryStateStore();
        intents = new IntentStore(store, TestSupport.mapper());
        listener = new NotificationListener(sender, roles, intents, new DedupeStore(store), TestSupport.props());
    }

    private static PaymentDoneEvent payment(UUID id, String status) {
        return new PaymentDoneEvent(id, CUSTOMER, status, 500, "ORD-1", "SGL1", null, null, Instant.now());
    }

    @Test
    @SuppressWarnings("unchecked")
    void successfulPaymentTellsTheCustomerAndOffersTheReceipt() {
        UUID id = UUID.randomUUID();

        listener.onPaymentDone(payment(id, "SUCCESS"));

        ArgumentCaptor<List<Button>> buttons = ArgumentCaptor.forClass((Class<List<Button>>) (Class<?>) List.class);
        verify(sender).notifyWithButtons(eq(CUSTOMER), contains("Payment received"), isNull(), buttons.capture());
        assertThat(buttons.getValue()).extracting(Button::id).containsExactly("RCPT:" + id);
    }

    @Test
    void aDuplicateEventFromTheBrokerIsNotSentTwice() {
        UUID id = UUID.randomUUID();

        listener.onPaymentDone(payment(id, "SUCCESS"));
        listener.onPaymentDone(payment(id, "SUCCESS"));

        verify(sender, times(1)).notifyWithButtons(anyString(), anyString(), any(), any());
    }

    @Test
    void ifSendingFailsTheEventCanBeRetried() {
        UUID id = UUID.randomUUID();
        doThrow(new RuntimeException("gateway down")).doNothing()
                .when(sender).notifyWithButtons(anyString(), anyString(), any(), any());

        assertThatThrownBy(() -> listener.onPaymentDone(payment(id, "SUCCESS"))).hasMessage("gateway down");
        listener.onPaymentDone(payment(id, "SUCCESS"));

        verify(sender, times(2)).notifyWithButtons(anyString(), anyString(), any(), any());
    }

    @Test
    void failedPaymentsGetNoReceiptButton() {
        listener.onPaymentDone(payment(UUID.randomUUID(), "FAILED"));

        verify(sender).notifyWithButtons(eq(CUSTOMER), contains("not been charged"), isNull(), eq(List.of()));
    }

    @Test
    void paymentsStillInProgressAreIgnored() {
        listener.onPaymentDone(payment(UUID.randomUUID(), "PUSHED"));

        verifyNoInteractions(sender);
    }

    @Test
    void aFailedRefundAlertsTheTeamAsWellAsTheCustomer() {
        when(roles.identify(CUSTOMER)).thenReturn(new Identity(CUSTOMER, Role.CUSTOMER, null));
        OperationDoneEvent event = new OperationDoneEvent(UUID.randomUUID(), "REFUND", "FAILED", CUSTOMER, null, CUSTOMER, 300,
                null, "Receiver not registered", null, Instant.now());

        listener.onOperationDone(event);

        verify(sender).notifyText(eq(CUSTOMER), contains("team has been alerted"));
        verify(sender).notifyText(eq(TEAM), contains("did not complete"));
    }

    @Test
    void aSuccessfulRefundDoesNotAlertTheTeam() {
        when(roles.identify(CUSTOMER)).thenReturn(new Identity(CUSTOMER, Role.CUSTOMER, null));
        OperationDoneEvent event = new OperationDoneEvent(UUID.randomUUID(), "REFUND", "SUCCESS", CUSTOMER, null, CUSTOMER, 300,
                "QWE1", null, null, Instant.now());

        listener.onOperationDone(event);

        verify(sender).notifyText(eq(CUSTOMER), contains("Refund sent"));
        verify(sender, never()).notifyText(eq(TEAM), anyString());
    }

    private static ApprovalRequestedEvent approval(UUID id, List<String> approvers) {
        return new ApprovalRequestedEvent(id, "DISBURSEMENT", CUSTOMER, "254722333444", 5000, "Supplier", approvers, Instant.now());
    }

    @Test
    @SuppressWarnings("unchecked")
    void eachApproverGetsTheirOwnSingleUseButtons() {
        UUID id = UUID.randomUUID();

        listener.onApprovalRequested(approval(id, List.of(APPROVER_A, APPROVER_B)));

        ArgumentCaptor<List<Button>> buttons = ArgumentCaptor.forClass((Class<List<Button>>) (Class<?>) List.class);
        verify(sender).notifyWithButtons(eq(APPROVER_A), contains("Approval needed"), isNull(), buttons.capture());
        verify(sender).notifyWithButtons(eq(APPROVER_B), contains("Approval needed"), isNull(), any());

        String approveId = buttons.getValue().get(0).id();
        assertThat(approveId).startsWith("APPROVE:");
        String intentId = approveId.substring("APPROVE:".length());
        // approver B cannot use approver A's button
        assertThat(intents.take(intentId, APPROVER_B, IntentType.APPROVAL)).isEmpty();
        assertThat(intents.take(intentId, APPROVER_A, IntentType.APPROVAL)).isPresent();
    }

    @Test
    void theRequesterIsNeverAskedToApproveTheirOwnRequest() {
        listener.onApprovalRequested(approval(UUID.randomUUID(), List.of(CUSTOMER, APPROVER_A)));

        verify(sender, never()).notifyWithButtons(eq(CUSTOMER), anyString(), any(), any());
        verify(sender).notifyWithButtons(eq(APPROVER_A), anyString(), any(), any());
    }

    @Test
    void oneUnreachableApproverDoesNotStopTheOthers() {
        doThrow(new RuntimeException("no route")).when(sender).notifyWithButtons(eq(APPROVER_A), anyString(), any(), any());

        listener.onApprovalRequested(approval(UUID.randomUUID(), List.of(APPROVER_A, APPROVER_B)));

        verify(sender).notifyWithButtons(eq(APPROVER_B), anyString(), any(), any());
    }

    @Test
    void noApproversMeansNothingIsSent() {
        listener.onApprovalRequested(approval(UUID.randomUUID(), List.of()));

        verifyNoInteractions(sender);
    }

    @Test
    void directPaymentsAreMatchedToACustomerWhenTheAccountNumberIsTheirPhone() {
        C2bReceivedEvent event = new C2bReceivedEvent("SGL999", new BigDecimal("1500"), "0712345678", "JOHN", "20260101", Instant.now());

        listener.onC2bReceived(event);
        listener.onC2bReceived(event);

        verify(sender, times(1)).notifyText(eq("254712345678"), contains("KES 1,500"));
    }

    @Test
    void directPaymentsWithAnyOtherAccountNumberAreNotSentAnywhere() {
        listener.onC2bReceived(new C2bReceivedEvent("SGL1000", new BigDecimal("100"), "INV-77", "JANE", "20260101", Instant.now()));

        verifyNoInteractions(sender);
    }

    @Test
    void aReportedWalletRequestIsAcknowledgedOnce() {
        WalletActionEvent event = new WalletActionEvent(UUID.randomUUID(), CUSTOMER, "PAY_TILL", "DONE_REPORTED", 300,
                "Pay KES 300 to Till 123456", null, null, Instant.now());

        listener.onWalletAction(event);
        listener.onWalletAction(event);

        verify(sender, times(1)).notifyText(eq(CUSTOMER), contains("Pay KES 300 to Till 123456"));
    }

    @Test
    void aLaterMessageMatchIsAnnouncedEvenAfterTheFirstReport() {
        WalletActionEvent first = new WalletActionEvent(UUID.randomUUID(), CUSTOMER, "PAY_TILL", "DONE_REPORTED", 300,
                "Pay KES 300 to Till 123456", null, "REPORTED", Instant.now());
        WalletActionEvent later = new WalletActionEvent(first.actionId(), CUSTOMER, "PAY_TILL", "DONE_REPORTED", 300,
                "Pay KES 300 to Till 123456", "SGL1234567", "SMS_AMOUNT", Instant.now());

        listener.onWalletAction(first);
        listener.onWalletAction(later);

        verify(sender, times(2)).notifyText(eq(CUSTOMER), anyString());
    }

    @Test
    void walletEventsWithoutAMessageOrAValidNumberAreIgnored() {
        listener.onWalletAction(new WalletActionEvent(UUID.randomUUID(), CUSTOMER, "PAY_TILL", "HANDED_OFF", 300, "x", null, null, Instant.now()));
        listener.onWalletAction(new WalletActionEvent(UUID.randomUUID(), "12345", "PAY_TILL", "DECLINED", 300, "x", null, null, Instant.now()));

        verifyNoInteractions(sender);
    }
}
