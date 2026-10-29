package com.mpesaagent.payment.service;

import com.mpesaagent.payment.config.OperationsProperties;
import com.mpesaagent.payment.config.StaffProperties;
import com.mpesaagent.payment.dto.CreateOperationRequest;
import com.mpesaagent.payment.dto.DarajaAsyncResponse;
import com.mpesaagent.payment.exception.ForbiddenOperationException;
import com.mpesaagent.payment.exception.IdempotencyConflictException;
import com.mpesaagent.payment.exception.InvalidPaymentRequestException;
import com.mpesaagent.payment.exception.LimitExceededException;
import com.mpesaagent.payment.exception.PaymentNotFoundException;
import com.mpesaagent.payment.model.MpesaOperation;
import com.mpesaagent.payment.model.OperationStatus;
import com.mpesaagent.payment.model.OperationType;
import com.mpesaagent.payment.model.Payment;
import com.mpesaagent.payment.model.PaymentStatus;
import com.mpesaagent.payment.model.StaffRole;
import com.mpesaagent.payment.repository.MpesaOperationRepository;
import com.mpesaagent.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationServiceTest {

    private static final String CUSTOMER = "254700000001";
    private static final String OTHER_CUSTOMER = "254700000002";
    private static final String STAFF = "254711000001";
    private static final String APPROVER = "254711000002";
    private static final String ADMIN = "254711000003";
    private static final UUID PAYMENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OP_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock MpesaOperationRepository repository;
    @Mock OperationStateService state;
    @Mock MpesaOperationsClient client;
    @Mock PaymentRepository payments;

    private OperationService service;

    @BeforeEach
    void setUp() {
        StaffProperties props = new StaffProperties(List.of(
                new StaffProperties.Member("0711000001", "Sam", StaffRole.STAFF),
                new StaffProperties.Member("0711000002", "Amina", StaffRole.APPROVER),
                new StaffProperties.Member("0711000003", "Ali", StaffRole.ADMIN)));
        service = new OperationService(repository, state, client, new StaffService(props), payments,
                new OperationsProperties(5000, 8000, false));
    }

    private Payment paidBy(String phone, int amount, PaymentStatus status) {
        Payment p = new Payment("pay-key", phone, amount, "ORD-1");
        ReflectionTestUtils.setField(p, "id", PAYMENT_ID);
        p.setStatus(status);
        return p;
    }

    private void stubSave() {
        when(repository.saveAndFlush(any(MpesaOperation.class))).thenAnswer(inv -> {
            MpesaOperation op = inv.getArgument(0);
            ReflectionTestUtils.setField(op, "id", OP_ID);
            return op;
        });
    }

    private static CreateOperationRequest refund(String actor, Integer amount) {
        return new CreateOperationRequest("key-1", OperationType.REFUND, actor, amount, null, null, PAYMENT_ID, "wrong item");
    }

    private static CreateOperationRequest payout(String actor, int amount) {
        return new CreateOperationRequest("key-2", OperationType.DISBURSEMENT, actor, amount, "0722000000", null, null, "supplier");
    }

    @Test
    void customerCanRequestRefundOfOwnSuccessfulPayment() {
        when(payments.findById(PAYMENT_ID)).thenReturn(Optional.of(paidBy(CUSTOMER, 500, PaymentStatus.SUCCESS)));
        stubSave();
        MpesaOperation announced = new MpesaOperation("key-1", OperationType.REFUND, CUSTOMER);
        when(state.announceApproval(OP_ID)).thenReturn(announced);

        MpesaOperation result = service.request(refund(CUSTOMER, null));

        assertThat(result).isSameAs(announced);
        verify(repository).saveAndFlush(any(MpesaOperation.class));
        verify(state).announceApproval(OP_ID);
        verify(state, never()).prepareSubmit(any());
    }

    @Test
    void refundGoesBackToWhoeverPaidAndDefaultsToFullAmount() {
        when(payments.findById(PAYMENT_ID)).thenReturn(Optional.of(paidBy(CUSTOMER, 500, PaymentStatus.SUCCESS)));
        stubSave();
        when(state.announceApproval(OP_ID)).thenAnswer(inv -> null);

        service.request(refund(CUSTOMER, null));

        var captor = org.mockito.ArgumentCaptor.forClass(MpesaOperation.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getRecipientPhone()).isEqualTo(CUSTOMER);
        assertThat(captor.getValue().getAmount()).isEqualTo(500);
        assertThat(captor.getValue().getStatus()).isEqualTo(OperationStatus.PENDING_APPROVAL);
    }

    @Test
    void customerCannotRefundSomeoneElsesPayment() {
        when(payments.findById(PAYMENT_ID)).thenReturn(Optional.of(paidBy(OTHER_CUSTOMER, 500, PaymentStatus.SUCCESS)));

        assertThatThrownBy(() -> service.request(refund(CUSTOMER, null)))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(state);
    }

    @Test
    void refundOfUnsuccessfulPaymentIsRejected() {
        when(payments.findById(PAYMENT_ID)).thenReturn(Optional.of(paidBy(CUSTOMER, 500, PaymentStatus.FAILED)));

        assertThatThrownBy(() -> service.request(refund(CUSTOMER, null)))
                .isInstanceOf(InvalidPaymentRequestException.class);
    }

    @Test
    void refundCannotExceedWhatIsLeftToRefund() {
        when(payments.findById(PAYMENT_ID)).thenReturn(Optional.of(paidBy(CUSTOMER, 500, PaymentStatus.SUCCESS)));
        when(repository.sumForPayment(eq(PAYMENT_ID), eq(OperationType.REFUND), anyCollection())).thenReturn(300L);

        assertThatThrownBy(() -> service.request(refund(CUSTOMER, 250)))
                .isInstanceOf(InvalidPaymentRequestException.class)
                .hasMessageContaining("200");
    }

    @Test
    void refundOfUnknownPaymentIsNotFound() {
        when(payments.findById(PAYMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.request(refund(CUSTOMER, null)))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void customerCannotRequestPayoutBalanceStatusOrReversal() {
        for (OperationType type : List.of(OperationType.DISBURSEMENT, OperationType.WITHDRAWAL, OperationType.ACCOUNT_BALANCE,
                OperationType.TRANSACTION_STATUS, OperationType.REVERSAL)) {
            CreateOperationRequest req = new CreateOperationRequest("k-" + type, type, CUSTOMER, 100, "0722000000", "ABC123", null, null);
            assertThatThrownBy(() -> service.request(req))
                    .as(type.name())
                    .isInstanceOf(ForbiddenOperationException.class);
        }
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void staffCanLookUpTransactionsButNotReadBalanceOrReverse() {
        stubSave();
        when(state.prepareSubmit(OP_ID)).thenReturn(statusOp());
        when(client.transactionStatus(anyString(), anyString())).thenReturn(accepted());
        when(state.recordConversation(eq(OP_ID), anyString())).thenReturn(statusOp());

        service.request(new CreateOperationRequest("k-s", OperationType.TRANSACTION_STATUS, STAFF, null, null, "qgh7abc123", null, null));
        verify(client).transactionStatus(eq("QGH7ABC123"), anyString());

        assertThatThrownBy(() -> service.request(
                new CreateOperationRequest("k-b", OperationType.ACCOUNT_BALANCE, STAFF, null, null, null, null, null)))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.request(
                new CreateOperationRequest("k-r", OperationType.REVERSAL, STAFF, 100, null, "QGH7ABC123", null, null)))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void adminCanReadBalance() {
        stubSave();
        MpesaOperation op = new MpesaOperation("k-b", OperationType.ACCOUNT_BALANCE, ADMIN);
        when(state.prepareSubmit(OP_ID)).thenReturn(op);
        when(client.accountBalance(anyString())).thenReturn(accepted());
        when(state.recordConversation(eq(OP_ID), anyString())).thenReturn(op);

        service.request(new CreateOperationRequest("k-b", OperationType.ACCOUNT_BALANCE, ADMIN, null, null, null, null, null));

        verify(client).accountBalance(anyString());
    }

    @Test
    void payoutWaitsForApprovalAndIsNeverSentImmediately() {
        stubSave();
        when(state.announceApproval(OP_ID)).thenAnswer(inv -> null);

        service.request(payout(STAFF, 1000));

        verify(state).announceApproval(OP_ID);
        verify(client, never()).b2c(anyString(), org.mockito.ArgumentMatchers.anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    void payoutAboveSingleLimitIsRejected() {
        assertThatThrownBy(() -> service.request(payout(STAFF, 5001)))
                .isInstanceOf(LimitExceededException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void payoutThatBreaksDailyLimitIsRejected() {
        when(repository.sumAmountSince(anyCollection(), anyCollection(), any())).thenReturn(7500L);

        assertThatThrownBy(() -> service.request(payout(STAFF, 1000)))
                .isInstanceOf(LimitExceededException.class)
                .hasMessageContaining("daily");
    }

    @Test
    void requesterCannotApproveOwnPayoutOrPersonWithoutApproverRole() {
        assertThatThrownBy(() -> service.approve(OP_ID, STAFF)).isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.approve(OP_ID, CUSTOMER)).isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.reject(OP_ID, CUSTOMER, "no")).isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.pendingApprovals(STAFF)).isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(state);
    }

    @Test
    void approverApprovalSubmitsToMpesa() {
        MpesaOperation op = new MpesaOperation("key-2", OperationType.DISBURSEMENT, STAFF);
        op.setRecipientPhone("254722000000");
        op.setAmount(1000);
        op.setOriginatorConversationId("orig-1");
        ReflectionTestUtils.setField(op, "id", OP_ID);
        when(state.approve(OP_ID, APPROVER)).thenReturn(op);
        when(state.prepareSubmit(OP_ID)).thenReturn(op);
        when(client.b2c(eq("orig-1"), eq(1000), eq("254722000000"), anyString(), anyString())).thenReturn(accepted());
        when(state.recordConversation(eq(OP_ID), anyString())).thenReturn(op);

        service.approve(OP_ID, APPROVER);

        verify(client).b2c(eq("orig-1"), eq(1000), eq("254722000000"), anyString(), anyString());
        verify(state).recordConversation(eq(OP_ID), eq("AG_1"));
    }

    @Test
    void sameKeyWithDifferentTypeIsAConflict() {
        MpesaOperation existing = new MpesaOperation("key-1", OperationType.DISBURSEMENT, STAFF);
        when(repository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.request(refund(STAFF, null)))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void sameKeyAndSameRequestReturnsExistingOperation() {
        MpesaOperation existing = new MpesaOperation("key-2", OperationType.DISBURSEMENT, STAFF);
        existing.setAmount(1000);
        when(repository.findByIdempotencyKey("key-2")).thenReturn(Optional.of(existing));

        assertThat(service.request(payout(STAFF, 1000))).isSameAs(existing);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void customersSeeOnlyTheirOwnOperations() {
        MpesaOperation op = new MpesaOperation("key-1", OperationType.REFUND, CUSTOMER);
        when(repository.findById(OP_ID)).thenReturn(Optional.of(op));

        assertThat(service.get(OP_ID, CUSTOMER)).isSameAs(op);
        assertThat(service.get(OP_ID, STAFF)).isSameAs(op);
        assertThatThrownBy(() -> service.get(OP_ID, OTHER_CUSTOMER)).isInstanceOf(PaymentNotFoundException.class);
    }

    private static MpesaOperation statusOp() {
        MpesaOperation op = new MpesaOperation("k-s", OperationType.TRANSACTION_STATUS, STAFF);
        op.setTargetReceipt("QGH7ABC123");
        ReflectionTestUtils.setField(op, "id", OP_ID);
        return op;
    }

    private static DarajaAsyncResponse accepted() {
        return new DarajaAsyncResponse("AG_1", "orig-1", "0", "Accept the service request successfully.");
    }
}
