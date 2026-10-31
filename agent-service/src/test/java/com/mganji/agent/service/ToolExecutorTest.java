package com.mganji.agent.service;

import com.mganji.agent.TestSupport;
import com.mganji.agent.client.PaymentClient;
import com.mganji.agent.dto.OperationView;
import com.mganji.agent.dto.PaymentView;
import com.mganji.agent.exception.PaymentApiException;
import com.mganji.agent.model.Identity;
import com.mganji.agent.model.Role;
import com.mganji.agent.store.InMemoryStateStore;
import com.mganji.agent.store.IntentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolExecutorTest {

    private static final String CUSTOMER = "254700000001";
    private static final String STAFF = "254711000001";
    private static final String APPROVER = "254711000002";
    private static final String ADMIN = "254711000003";
    private static final UUID PAYMENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OP_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock PaymentClient payments;
    @Mock ReplySender sender;
    @Mock com.mganji.agent.client.GanjiClient ganji;

    private IntentStore intents;
    private ToolExecutor executor;

    @BeforeEach
    void setUp() {
        intents = new IntentStore(new InMemoryStateStore(), TestSupport.mapper());
        executor = new ToolExecutor(payments, intents, sender, TestSupport.props(), TestSupport.business(),
                new WalletTools(ganji, new com.mganji.agent.config.ServiceProperties.Ganji("http://ganji", "https://app.example.test")));
    }

    private static ToolContext ctx(String phone, Role role) {
        return new ToolContext(new Identity(phone, role, null), "wamid.1");
    }

    private static JsonNode json(String text) {
        try {
            return TestSupport.mapper().readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static PaymentView payment(String status, String phone, int amount) {
        return new PaymentView(PAYMENT_ID, status, phone, amount, "ORD-1", "ws_CO_1", "QGH7ABC123", "0", null,
                Instant.parse("2026-10-29T09:00:00Z"), Instant.parse("2026-10-29T09:01:00Z"));
    }

    // ---------------------------------------------------------------- role gating

    @Test
    void customersCannotUseAnyStaffTool() {
        ToolContext customer = ctx(CUSTOMER, Role.CUSTOMER);
        for (String tool : List.of(ToolCatalog.LOOKUP_TRANSACTION, ToolCatalog.LOOKUP_CUSTOMER_PAYMENTS, ToolCatalog.REQUEST_PAYOUT,
                ToolCatalog.LIST_PENDING_APPROVALS, ToolCatalog.REVIEW_APPROVAL, ToolCatalog.ACCOUNT_BALANCE, ToolCatalog.REQUEST_REVERSAL)) {
            ToolExecutor.Result result = executor.execute(customer, tool, json("{\"receipt\":\"QGH7ABC123\",\"phone\":\"0712345678\"}"));
            assertThat(result.error()).as(tool).isTrue();
            assertThat(result.content()).as(tool).contains("do not have permission");
        }
        verifyNoInteractions(payments, sender);
    }

    @Test
    void eachStaffLevelOnlyGetsItsOwnTools() {
        assertThat(executor.execute(ctx(STAFF, Role.STAFF), ToolCatalog.LIST_PENDING_APPROVALS, json("{}")).error()).isTrue();
        assertThat(executor.execute(ctx(STAFF, Role.STAFF), ToolCatalog.ACCOUNT_BALANCE, json("{}")).error()).isTrue();
        assertThat(executor.execute(ctx(APPROVER, Role.APPROVER), ToolCatalog.ACCOUNT_BALANCE, json("{}")).error()).isTrue();
        assertThat(executor.execute(ctx(APPROVER, Role.APPROVER), ToolCatalog.REQUEST_REVERSAL,
                json("{\"receipt\":\"QGH7ABC123\",\"amount\":10,\"reason\":\"x\"}")).error()).isTrue();
        verifyNoInteractions(payments);
    }

    @Test
    void theToolListMatchesTheRole() {
        ToolCatalog catalog = new ToolCatalog(TestSupport.mapper());
        assertThat(names(catalog, Role.CUSTOMER)).contains("propose_payment", "request_refund", "escalate_to_human")
                .doesNotContain("request_payout", "account_balance", "review_approval");
        assertThat(names(catalog, Role.STAFF)).contains("request_payout", "lookup_transaction").doesNotContain("review_approval");
        assertThat(names(catalog, Role.APPROVER)).contains("review_approval", "list_pending_approvals").doesNotContain("account_balance");
        assertThat(names(catalog, Role.ADMIN)).contains("account_balance", "request_reversal");
    }

    private static List<String> names(ToolCatalog catalog, Role role) {
        return catalog.toolsFor(role).findValuesAsString("name");
    }

    // ---------------------------------------------------------------- payments

    @SuppressWarnings("unchecked")
	@Test
    void proposingAPaymentOnlyCreatesAConfirmationAndNeverCallsPaymentService() {
        ToolContext ctx = ctx(CUSTOMER, Role.CUSTOMER);

        ToolExecutor.Result result = executor.execute(ctx, ToolCatalog.PROPOSE_PAYMENT, json("{\"amount\":1500,\"reference\":\"ORD 77!\"}"));

        assertThat(result.error()).isFalse();
        assertThat(result.content()).contains("KES 1,500").contains("Nothing has been charged");
        assertThat(ctx.deferred()).hasSize(1);
        verifyNoInteractions(payments);

        ctx.deferred().get(0).accept(sender);
        @SuppressWarnings("rawtypes")
		ArgumentCaptor<List> buttons = ArgumentCaptor.forClass(List.class);
        verify(sender).buttons(eq(CUSTOMER), org.mockito.ArgumentMatchers.contains("KES 1,500"), anyString(), buttons.capture());
        assertThat(buttons.getValue().toString()).contains("PAY:").contains("NO:");
    }

    @Test
    void referenceIsCleanedToWhatDarajaAccepts() {
        assertThat(ToolExecutor.reference("ORD 77!")).isEqualTo("ORD77");
        assertThat(ToolExecutor.reference("A-VERY-LONG-REFERENCE-123")).hasSize(12);
        assertThat(ToolExecutor.reference("")).isEqualTo("CHAT");
        assertThat(ToolExecutor.reference("###")).isEqualTo("CHAT");
    }

    @Test
    void badAmountsAreRejectedBeforeAnythingIsPrepared() {
        for (String input : List.of("{}", "{\"amount\":0}", "{\"amount\":-5}", "{\"amount\":12.5}", "{\"amount\":\"abc\"}", "{\"amount\":null}")) {
            ToolContext ctx = ctx(CUSTOMER, Role.CUSTOMER);
            ToolExecutor.Result result = executor.execute(ctx, ToolCatalog.PROPOSE_PAYMENT, json(input));
            assertThat(result.error()).as(input).isTrue();
            assertThat(ctx.deferred()).as(input).isEmpty();
        }
    }

    @Test
    void amountWrittenAsTextWithCommasIsUnderstood() {
        ToolContext ctx = ctx(CUSTOMER, Role.CUSTOMER);
        assertThat(executor.execute(ctx, ToolCatalog.PROPOSE_PAYMENT, json("{\"amount\":\"1,200\"}")).content()).contains("KES 1,200");
    }

    @Test
    void nonKenyanNumbersCannotBePromptedForPayment() {
        ToolExecutor.Result result = executor.execute(ctx("12025550123", Role.CUSTOMER), ToolCatalog.PROPOSE_PAYMENT, json("{\"amount\":100}"));
        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("Kenyan");
    }

    // ---------------------------------------------------------------- ownership

    @Test
    void customersCanOnlyLookAtTheirOwnPayments() {
        when(payments.getPayment(PAYMENT_ID, CUSTOMER)).thenThrow(new PaymentApiException(404, "Payment not found"));

        ToolExecutor.Result result = executor.execute(ctx(CUSTOMER, Role.CUSTOMER), ToolCatalog.CHECK_PAYMENT_STATUS,
                json("{\"payment_id\":\"" + PAYMENT_ID + "\"}"));

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("could not find that");
        verify(payments).getPayment(PAYMENT_ID, CUSTOMER);   // always scoped to the chat phone
    }

    @Test
    void statusWithoutAnIdUsesTheLatestPaymentOfThisPhone() {
        when(payments.latest(CUSTOMER)).thenReturn(Optional.of(payment("PUSHED", CUSTOMER, 500)));

        ToolExecutor.Result result = executor.execute(ctx(CUSTOMER, Role.CUSTOMER), ToolCatalog.CHECK_PAYMENT_STATUS, json("{}"));

        assertThat(result.content()).contains("KES 500").contains("PUSHED").contains("waiting for the customer to enter their PIN");
    }

    @Test
    void aPaymentIdThatIsNotAnIdIsRefusedWithoutCallingTheService() {
        ToolExecutor.Result result = executor.execute(ctx(CUSTOMER, Role.CUSTOMER), ToolCatalog.CHECK_PAYMENT_STATUS,
                json("{\"payment_id\":\"1; DROP TABLE\"}"));

        assertThat(result.error()).isTrue();
        verifyNoInteractions(payments);
    }

    // ---------------------------------------------------------------- refunds

    @Test
    void refundOfAnotherPersonsPaymentIsRefusedBecauseTheLookupIsScopedToTheCustomer() {
        when(payments.getPayment(PAYMENT_ID, CUSTOMER)).thenThrow(new PaymentApiException(404, "Payment not found"));

        ToolExecutor.Result result = executor.execute(ctx(CUSTOMER, Role.CUSTOMER), ToolCatalog.REQUEST_REFUND,
                json("{\"payment_id\":\"" + PAYMENT_ID + "\",\"reason\":\"wrong item\"}"));

        assertThat(result.error()).isTrue();
        verify(payments, never()).createOperation(any());
    }

    @Test
    void refundOnlyForSuccessfulPaymentsAndNeverMoreThanPaid() {
        when(payments.getPayment(PAYMENT_ID, CUSTOMER)).thenReturn(payment("FAILED", CUSTOMER, 500));
        assertThat(executor.execute(ctx(CUSTOMER, Role.CUSTOMER), ToolCatalog.REQUEST_REFUND,
                json("{\"payment_id\":\"" + PAYMENT_ID + "\",\"reason\":\"x\"}")).error()).isTrue();

        when(payments.getPayment(PAYMENT_ID, CUSTOMER)).thenReturn(payment("SUCCESS", CUSTOMER, 500));
        ToolExecutor.Result tooMuch = executor.execute(ctx(CUSTOMER, Role.CUSTOMER), ToolCatalog.REQUEST_REFUND,
                json("{\"payment_id\":\"" + PAYMENT_ID + "\",\"amount\":900,\"reason\":\"x\"}"));
        assertThat(tooMuch.error()).isTrue();
        assertThat(tooMuch.content()).contains("more than the payment");
    }

    @Test
    void validRefundRequestOnlyQueuesAConfirmation() {
        when(payments.getPayment(PAYMENT_ID, CUSTOMER)).thenReturn(payment("SUCCESS", CUSTOMER, 500));
        ToolContext ctx = ctx(CUSTOMER, Role.CUSTOMER);

        ToolExecutor.Result result = executor.execute(ctx, ToolCatalog.REQUEST_REFUND,
                json("{\"payment_id\":\"" + PAYMENT_ID + "\",\"amount\":200,\"reason\":\"wrong item\"}"));

        assertThat(result.error()).isFalse();
        assertThat(ctx.deferred()).hasSize(1);
        verify(payments, never()).createOperation(any());
    }

    @Test
    void staffMayLookUpAnyPaymentForARefund() {
        when(payments.getPayment(eq(PAYMENT_ID), isNull())).thenReturn(payment("SUCCESS", CUSTOMER, 500));

        ToolExecutor.Result result = executor.execute(ctx(STAFF, Role.STAFF), ToolCatalog.REQUEST_REFUND,
                json("{\"payment_id\":\"" + PAYMENT_ID + "\",\"reason\":\"customer called\"}"));

        assertThat(result.error()).isFalse();
    }

    // ---------------------------------------------------------------- staff

    @Test
    void payoutRequestValidatesTheRecipientAndOnlyQueuesAConfirmation() {
        ToolContext bad = ctx(STAFF, Role.STAFF);
        assertThat(executor.execute(bad, ToolCatalog.REQUEST_PAYOUT,
                json("{\"recipient_phone\":\"12345\",\"amount\":500,\"reason\":\"supplies\"}")).error()).isTrue();

        ToolContext ok = ctx(STAFF, Role.STAFF);
        ToolExecutor.Result result = executor.execute(ok, ToolCatalog.REQUEST_PAYOUT,
                json("{\"recipient_phone\":\"0722000111\",\"amount\":500,\"reason\":\"supplies\"}"));

        assertThat(result.error()).isFalse();
        assertThat(ok.deferred()).hasSize(1);
        verify(payments, never()).createOperation(any());
    }

    @Test
    void transactionLookupNeedsAReceiptShapedValue() {
        assertThat(executor.execute(ctx(STAFF, Role.STAFF), ToolCatalog.LOOKUP_TRANSACTION, json("{\"receipt\":\"hello there\"}")).error()).isTrue();

        when(payments.createOperation(any())).thenReturn(op("SUBMITTED", "TRANSACTION_STATUS", STAFF));
        ToolExecutor.Result ok = executor.execute(ctx(STAFF, Role.STAFF), ToolCatalog.LOOKUP_TRANSACTION, json("{\"receipt\":\"qgh7abc123\"}"));

        assertThat(ok.error()).isFalse();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
        verify(payments).createOperation(body.capture());
        assertThat(body.getValue()).containsEntry("type", "TRANSACTION_STATUS").containsEntry("targetReceipt", "QGH7ABC123")
                .containsEntry("actorPhone", STAFF);
    }

    // ---------------------------------------------------------------- approvals

    @Test
    void approverReviewCreatesAnApprovalIntentBoundToThatApprover() {
        when(payments.getOperation(OP_ID, APPROVER)).thenReturn(op("PENDING_APPROVAL", "DISBURSEMENT", STAFF));
        ToolContext ctx = ctx(APPROVER, Role.APPROVER);

        ToolExecutor.Result result = executor.execute(ctx, ToolCatalog.REVIEW_APPROVAL, json("{\"operation_id\":\"" + OP_ID + "\"}"));

        assertThat(result.error()).isFalse();
        assertThat(ctx.deferred()).hasSize(1);
        verify(payments, never()).approve(any(), anyString());
    }

    @Test
    void youCannotReviewYourOwnRequestAndDecidedRequestsAreNotReopened() {
        when(payments.getOperation(OP_ID, APPROVER)).thenReturn(op("PENDING_APPROVAL", "DISBURSEMENT", APPROVER));
        ToolContext own = ctx(APPROVER, Role.APPROVER);
        assertThat(executor.execute(own, ToolCatalog.REVIEW_APPROVAL, json("{\"operation_id\":\"" + OP_ID + "\"}")).content())
                .contains("made this request yourself");
        assertThat(own.deferred()).isEmpty();

        when(payments.getOperation(OP_ID, ADMIN)).thenReturn(op("SUCCESS", "DISBURSEMENT", STAFF));
        ToolContext done = ctx(ADMIN, Role.ADMIN);
        assertThat(executor.execute(done, ToolCatalog.REVIEW_APPROVAL, json("{\"operation_id\":\"" + OP_ID + "\"}")).content())
                .contains("already SUCCESS");
        assertThat(done.deferred()).isEmpty();
    }

    // ---------------------------------------------------------------- failures

    @Test
    void paymentServiceOutageBecomesAnHonestMessage() {
        when(payments.latest(CUSTOMER)).thenThrow(new PaymentApiException(0, "The payment service is not reachable right now"));

        ToolExecutor.Result result = executor.execute(ctx(CUSTOMER, Role.CUSTOMER), ToolCatalog.CHECK_PAYMENT_STATUS, json("{}"));

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("not available right now");
    }

    @Test
    void unexpectedExceptionsDoNotLeakDetails() {
        when(payments.latest(CUSTOMER)).thenThrow(new IllegalStateException("db password is hunter2"));

        ToolExecutor.Result result = executor.execute(ctx(CUSTOMER, Role.CUSTOMER), ToolCatalog.CHECK_PAYMENT_STATUS, json("{}"));

        assertThat(result.error()).isTrue();
        assertThat(result.content()).doesNotContain("hunter2");
    }

    @Test
    void unknownToolsAreErrors() {
        assertThat(executor.execute(ctx(CUSTOMER, Role.CUSTOMER), "format_disk", json("{}")).error()).isTrue();
    }

    private static OperationView op(String status, String type, String requestedBy) {
        return new OperationView(OP_ID, type, status, requestedBy, null, "254722000111", 500, null, null, "supplies",
                null, null, null, null, Instant.parse("2026-10-29T09:00:00Z"), Instant.parse("2026-10-29T09:00:00Z"));
    }
}