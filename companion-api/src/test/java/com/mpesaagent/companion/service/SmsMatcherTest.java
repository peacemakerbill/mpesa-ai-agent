package com.mpesaagent.companion.service;

import com.mpesaagent.companion.dto.SmsEvidenceRequest;
import com.mpesaagent.companion.dto.SmsEvidenceRequest.Kind;
import com.mpesaagent.companion.exception.InvalidActionRequestException;
import com.mpesaagent.companion.model.ActionSource;
import com.mpesaagent.companion.model.ActionType;
import com.mpesaagent.companion.model.Verification;
import com.mpesaagent.companion.model.WalletAction;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmsMatcherTest {

    private static final Instant NOW = TestSupport.NOW;
    private static final Instant APPROVED = NOW.minus(Duration.ofMinutes(3));

    private static WalletAction action(ActionType type, int amount, Map<String, String> details) {
        WalletAction a = new WalletAction("wa-test", "254700000001", type, amount, null, ActionSource.TEXT, details, NOW.plus(Duration.ofMinutes(30)));
        a.setApprovedAt(APPROVED);
        return a;
    }

    private static SmsEvidenceRequest sms(Kind kind, int amount, String payee, String account, Instant at) {
        return new SmsEvidenceRequest("SGL1234567", kind, amount, payee, account, at.toEpochMilli());
    }

    private static Instant recent() {
        return APPROVED.plus(Duration.ofMinutes(2));
    }

    @Test
    void sendMoneyMatchesOnTheLastNineDigitsOfTheNumber() {
        WalletAction a = action(ActionType.SEND_MONEY, 500, Map.of("recipientPhone", "254712345678"));

        assertThat(SmsMatcher.match(a, sms(Kind.SENT, 500, "JOHN DOE 0712345678", null, recent()), NOW)).isEqualTo(Verification.SMS_MATCHED);
        assertThat(SmsMatcher.match(a, sms(Kind.SENT, 500, "JOHN DOE 0712 345 678", null, recent()), NOW)).isEqualTo(Verification.SMS_MATCHED);
    }

    @Test
    void aDifferentNumberIsRefusedWithAWarning() {
        WalletAction a = action(ActionType.SEND_MONEY, 500, Map.of("recipientPhone", "254712345678"));

        assertThatThrownBy(() -> SmsMatcher.match(a, sms(Kind.SENT, 500, "JANE 0799999999", null, recent()), NOW))
                .isInstanceOf(InvalidActionRequestException.class)
                .hasMessageContaining("different number").hasMessageContaining("wrong number");
    }

    @Test
    void aContactRequestOrAnUnnamedPayeeIsOnlyAnAmountMatch() {
        WalletAction contact = action(ActionType.SEND_MONEY, 500, Map.of("contactName", "Mama"));
        WalletAction number = action(ActionType.SEND_MONEY, 500, Map.of("recipientPhone", "254712345678"));

        assertThat(SmsMatcher.match(contact, sms(Kind.SENT, 500, null, null, recent()), NOW)).isEqualTo(Verification.SMS_AMOUNT);
        assertThat(SmsMatcher.match(number, sms(Kind.SENT, 500, "MAMA", null, recent()), NOW)).isEqualTo(Verification.SMS_AMOUNT);
    }

    @Test
    void aPaybillMatchesOnTheAccount() {
        WalletAction a = action(ActionType.PAY_BILL, 1200, Map.of("paybill", "888880", "account", "ACC 123-456"));

        assertThat(SmsMatcher.match(a, sms(Kind.BILL, 1200, "KPLC", "acc123456", recent()), NOW)).isEqualTo(Verification.SMS_MATCHED);
        assertThat(SmsMatcher.match(a, sms(Kind.BILL, 1200, "KPLC", null, recent()), NOW)).isEqualTo(Verification.SMS_AMOUNT);
        assertThatThrownBy(() -> SmsMatcher.match(a, sms(Kind.BILL, 1200, "KPLC", "999999", recent()), NOW))
                .isInstanceOf(InvalidActionRequestException.class).hasMessageContaining("different account");
    }

    @Test
    void tillAndAirtimeCanOnlyMatchOnAmountUnlessTheNumberIsShown() {
        WalletAction till = action(ActionType.PAY_TILL, 300, Map.of("till", "123456"));
        WalletAction airtime = action(ActionType.BUY_AIRTIME, 50, Map.of());
        WalletAction other = action(ActionType.BUY_AIRTIME, 50, Map.of("airtimePhone", "254722000111"));

        assertThat(SmsMatcher.match(till, sms(Kind.PAID, 300, "SHOP", null, recent()), NOW)).isEqualTo(Verification.SMS_AMOUNT);
        assertThat(SmsMatcher.match(airtime, sms(Kind.AIRTIME, 50, null, null, recent()), NOW)).isEqualTo(Verification.SMS_AMOUNT);
        assertThat(SmsMatcher.match(other, sms(Kind.AIRTIME, 50, "0722000111", null, recent()), NOW)).isEqualTo(Verification.SMS_MATCHED);
    }

    @Test
    void theAmountMustBeExactlyTheSame() {
        WalletAction a = action(ActionType.PAY_TILL, 300, Map.of("till", "123456"));

        assertThatThrownBy(() -> SmsMatcher.match(a, sms(Kind.PAID, 301, "SHOP", null, recent()), NOW))
                .isInstanceOf(InvalidActionRequestException.class).hasMessageContaining("KES 301").hasMessageContaining("KES 300");
    }

    @Test
    void theKindOfTransactionMustFit() {
        WalletAction a = action(ActionType.PAY_TILL, 300, Map.of("till", "123456"));

        assertThatThrownBy(() -> SmsMatcher.match(a, sms(Kind.SENT, 300, "SHOP", null, recent()), NOW))
                .isInstanceOf(InvalidActionRequestException.class).hasMessageContaining("different kind");
        // Pochi may be worded either way
        WalletAction pochi = action(ActionType.PAY_POCHI, 300, Map.of("pochiPhone", "254722000111"));
        assertThat(SmsMatcher.match(pochi, sms(Kind.PAID, 300, "SHOP", null, recent()), NOW)).isEqualTo(Verification.SMS_AMOUNT);
    }

    @Test
    void theMessageMustBeFromAroundTheRequest() {
        WalletAction a = action(ActionType.PAY_TILL, 300, Map.of("till", "123456"));

        // a message from before the request (an old payment for the same amount)
        assertThatThrownBy(() -> SmsMatcher.match(a, sms(Kind.PAID, 300, "SHOP", null, APPROVED.minus(Duration.ofMinutes(10))), NOW))
                .isInstanceOf(InvalidActionRequestException.class).hasMessageContaining("around the time");
        // slightly early is tolerated, far in the future is not
        assertThat(SmsMatcher.match(a, sms(Kind.PAID, 300, "SHOP", null, APPROVED.minus(Duration.ofMinutes(1))), NOW)).isEqualTo(Verification.SMS_AMOUNT);
        assertThatThrownBy(() -> SmsMatcher.match(a, sms(Kind.PAID, 300, "SHOP", null, NOW.plus(Duration.ofMinutes(30))), NOW))
                .isInstanceOf(InvalidActionRequestException.class);
    }

    @Test
    void aRatibaHasNoPaymentMessage() {
        WalletAction a = action(ActionType.SCHEDULE_RATIBA, 300, Map.of("target", "PAY_TILL"));

        assertThatThrownBy(() -> SmsMatcher.match(a, sms(Kind.PAID, 300, "SHOP", null, recent()), NOW))
                .isInstanceOf(InvalidActionRequestException.class).hasMessageContaining("Ratiba");
    }

    @Test
    void verificationLevelsOnlyGoUp() {
        WalletAction a = action(ActionType.PAY_TILL, 300, Map.of("till", "123456"));
        a.raiseVerification(Verification.SMS_MATCHED);
        a.raiseVerification(Verification.REPORTED);

        assertThat(a.getVerification()).isEqualTo(Verification.SMS_MATCHED);
    }
}
