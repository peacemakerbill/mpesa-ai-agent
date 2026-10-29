package com.mpesaagent.agent.service;

import com.mpesaagent.agent.dto.ApprovalRequestedEvent;
import com.mpesaagent.agent.dto.C2bReceivedEvent;
import com.mpesaagent.agent.dto.OperationDoneEvent;
import com.mpesaagent.agent.dto.PaymentDoneEvent;
import com.mpesaagent.agent.dto.WalletActionEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationsTest {

    private static PaymentDoneEvent payment(String status, String receipt, String reason) {
        return new PaymentDoneEvent(UUID.randomUUID(), "254700000001", status, 500, "ORD-1", receipt, null, reason, Instant.now());
    }

    private static OperationDoneEvent op(String type, String status, String reason, Map<String, String> details) {
        return new OperationDoneEvent(UUID.randomUUID(), type, status, "254700000001", null, "254722333444", 1200,
                "QWE123", reason, details, Instant.now());
    }

    @Test
    void successfulPaymentThanksTheCustomerAndShowsTheReceipt() {
        String text = Notifications.payment(payment("SUCCESS", "SGL1234567", null));

        assertThat(text).contains("Payment received").contains("KES 500").contains("ORD-1").contains("SGL1234567");
    }

    @Test
    void failedPaymentExplainsTheReasonAndSaysNothingWasCharged() {
        String text = Notifications.payment(payment("FAILED", null, "Insufficient funds"));

        assertThat(text).contains("not been charged").contains("Insufficient funds");
    }

    @Test
    void cancelledAndExpiredPromptsAreFriendly() {
        assertThat(Notifications.payment(payment("CANCELLED", null, null))).contains("cancelled");
        assertThat(Notifications.payment(payment("TIMEOUT", null, null))).contains("expired");
    }

    @Test
    void paymentsThatAreNotFinishedProduceNoMessage() {
        assertThat(Notifications.payment(payment("PUSHED", null, null))).isNull();
        assertThat(Notifications.payment(payment(null, null, null))).isNull();
    }

    @Test
    void refundSuccessTellsTheCustomerItIsSent() {
        assertThat(Notifications.operation(op("REFUND", "SUCCESS", null, null), false))
                .contains("Refund sent").contains("KES 1,200").contains("QWE123");
    }

    @Test
    void refundFailureIsGentleForCustomersAndDetailedForStaff() {
        String customer = Notifications.operation(op("REFUND", "FAILED", "Receiver blocked", null), false);
        String staff = Notifications.operation(op("REFUND", "FAILED", "Receiver blocked", null), true);

        assertThat(customer).contains("team has been alerted").doesNotContain("Receiver blocked");
        assertThat(staff).contains("Receiver blocked");
    }

    @Test
    void payoutTimeoutWarnsThatMoneyMayStillMove() {
        String text = Notifications.operation(op("DISBURSEMENT", "TIMEOUT", null, null), true);

        assertThat(text).contains("may still go through").contains("254722***444").doesNotContain("254722333444");
    }

    @Test
    void balanceListsTheDetails() {
        String text = Notifications.operation(op("ACCOUNT_BALANCE", "SUCCESS", null, Map.of("Working Account", "KES 10,000")), true);

        assertThat(text).contains("Account balance").contains("Working Account: KES 10,000");
    }

    @Test
    void unknownOperationTypesAndStatesAreIgnored() {
        assertThat(Notifications.operation(op("SOMETHING_NEW", "SUCCESS", null, null), true)).isNull();
        assertThat(Notifications.operation(op("REFUND", "SUBMITTED", null, null), true)).isNull();
    }

    @Test
    void approvalRequestMasksNumbersAndShowsTheReason() {
        ApprovalRequestedEvent e = new ApprovalRequestedEvent(UUID.randomUUID(), "DISBURSEMENT", "254700000001", "254722333444",
                5000, "Supplier payment", List.of("254711000002"), Instant.now());

        String text = Notifications.approval(e);

        assertThat(text).contains("Approval needed").contains("KES 5,000").contains("Supplier payment")
                .contains("254722***444").contains("254700***001")
                .doesNotContain("254722333444").doesNotContain("254700000001");
    }

    @Test
    void directPaybillPaymentsAreAcknowledged() {
        C2bReceivedEvent e = new C2bReceivedEvent("SGL999", new BigDecimal("1500.00"), "0712345678", "JOHN", "20260101120000", Instant.now());

        assertThat(Notifications.c2b(e)).contains("KES 1,500").contains("SGL999").contains("0712345678");
    }

    private static WalletActionEvent wallet(String status, String receipt) {
        return new WalletActionEvent(UUID.randomUUID(), "254700000001", "PAY_TILL", status, 300, "Pay KES 300 to Till 123456", receipt, null, Instant.now());
    }

    @Test
    void aReportedCompletionAsksForTheConfirmationMessageWhenThereIsNoCodeYet() {
        String text = Notifications.walletAction(wallet("DONE_REPORTED", null));

        assertThat(text).contains("Pay KES 300 to Till 123456").contains("forward it or send a screenshot");
    }

    @Test
    void aReportedCompletionWithACodeShowsItAsReportedNotVerified() {
        String text = Notifications.walletAction(wallet("DONE_REPORTED", "SGL1234567"));

        assertThat(text).contains("SGL1234567").contains("recorded").doesNotContain("verified").doesNotContain("confirmed by");
    }

    @Test
    void anUnfinishedRequestOffersHelpWithoutAssumingMoneyMoved() {
        String text = Notifications.walletAction(wallet("NOT_DONE", null));

        assertThat(text).contains("not done").contains("connect you to a person");
    }

    @Test
    void aDeclinedRequestSaysNothingWasSent() {
        assertThat(Notifications.walletAction(wallet("DECLINED", null))).contains("nothing was sent");
    }

    @Test
    void aMessageFromTheCustomersPhoneIsDescribedAsThatAndNotAsSafaricom() {
        WalletActionEvent matched = new WalletActionEvent(UUID.randomUUID(), "254700000001", "PAY_BILL", "DONE_REPORTED", 1200,
                "Pay KES 1,200 to Paybill 888880", "SGL1234567", "SMS_MATCHED", Instant.now());
        WalletActionEvent amountOnly = new WalletActionEvent(UUID.randomUUID(), "254700000001", "PAY_TILL", "DONE_REPORTED", 300,
                "Pay KES 300 to Till 123456", "SGL1234567", "SMS_AMOUNT", Instant.now());

        assertThat(Notifications.walletAction(matched)).contains("matches this request").contains("SGL1234567")
                .contains("on your own phone").contains("Safaricom has not confirmed");
        assertThat(Notifications.walletAction(amountOnly)).contains("check the name").contains("Safaricom has not confirmed");
    }

    @Test
    void otherWalletStatusesProduceNoMessage() {
        assertThat(Notifications.walletAction(wallet("HANDED_OFF", null))).isNull();
        assertThat(Notifications.walletAction(wallet(null, null))).isNull();
    }
}
