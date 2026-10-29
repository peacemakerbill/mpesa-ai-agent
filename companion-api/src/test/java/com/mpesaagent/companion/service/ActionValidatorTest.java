package com.mpesaagent.companion.service;

import com.mpesaagent.companion.exception.InvalidActionRequestException;
import com.mpesaagent.companion.model.ActionType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionValidatorTest {

    private static final String OWNER = "254700000001";

    private final ActionValidator validator = new ActionValidator(TestSupport.props(), TestSupport.clock());

    private ActionValidator.Validated check(ActionType type, int amount, Map<String, String> details) {
        return validator.validate(OWNER, type, amount, details, null);
    }

    @Test
    void sendMoneyToANumberIsNormalised() {
        var v = check(ActionType.SEND_MONEY, 500, Map.of("recipientPhone", "0712 345 678", "recipientName", "John Doe"));

        assertThat(v.details()).containsEntry("recipientPhone", "254712345678").containsEntry("recipientName", "John Doe");
    }

    @Test
    void sendMoneyToAContactNameKeepsOnlyTheName() {
        var v = check(ActionType.SEND_MONEY, 500, Map.of("contactName", "Mama"));

        assertThat(v.details()).containsOnlyKeys("contactName");
    }

    @Test
    void sendMoneyNeedsExactlyOneRecipient() {
        assertThatThrownBy(() -> check(ActionType.SEND_MONEY, 500, Map.of()))
                .isInstanceOf(InvalidActionRequestException.class);
        assertThatThrownBy(() -> check(ActionType.SEND_MONEY, 500, Map.of("recipientPhone", "0712345678", "contactName", "Mama")))
                .isInstanceOf(InvalidActionRequestException.class);
    }

    @Test
    void sendingToYourOwnNumberIsRefused() {
        assertThatThrownBy(() -> check(ActionType.SEND_MONEY, 500, Map.of("recipientPhone", "0700000001")))
                .hasMessageContaining("own number");
        assertThatThrownBy(() -> check(ActionType.PAY_POCHI, 500, Map.of("pochiPhone", "254700000001")))
                .hasMessageContaining("own number");
    }

    @Test
    void foreignOrShortNumbersAreRefused() {
        assertThatThrownBy(() -> check(ActionType.SEND_MONEY, 500, Map.of("recipientPhone", "+447911123456")))
                .isInstanceOf(InvalidActionRequestException.class);
        assertThatThrownBy(() -> check(ActionType.SEND_MONEY, 500, Map.of("recipientPhone", "12345")))
                .isInstanceOf(InvalidActionRequestException.class);
    }

    @Test
    void paybillNeedsAShortcodeAndAnAccount() {
        var v = check(ActionType.PAY_BILL, 1200, Map.of("paybill", "888 880", "account", "ACC-123/4", "businessName", "KPLC"));

        assertThat(v.details()).containsEntry("paybill", "888880").containsEntry("account", "ACC-123/4");
        assertThatThrownBy(() -> check(ActionType.PAY_BILL, 1200, Map.of("paybill", "12", "account", "A")))
                .hasMessageContaining("5 to 7 digits");
        assertThatThrownBy(() -> check(ActionType.PAY_BILL, 1200, Map.of("paybill", "888880")))
                .hasMessageContaining("account");
    }

    @Test
    void tillNumbersAreChecked() {
        assertThat(check(ActionType.PAY_TILL, 300, Map.of("till", "123456")).details()).containsEntry("till", "123456");
        assertThatThrownBy(() -> check(ActionType.PAY_TILL, 300, Map.of("till", "12AB56")))
                .isInstanceOf(InvalidActionRequestException.class);
    }

    @Test
    void airtimeDefaultsToTheOwnNumberAndHasAMinimum() {
        assertThat(check(ActionType.BUY_AIRTIME, 100, Map.of()).details()).isEmpty();
        assertThat(check(ActionType.BUY_AIRTIME, 100, Map.of("airtimePhone", "0722000111")).details())
                .containsEntry("airtimePhone", "254722000111");
        assertThatThrownBy(() -> check(ActionType.BUY_AIRTIME, 2, Map.of())).hasMessageContaining("smallest airtime");
    }

    @Test
    void amountsMustBePositiveAndUnderTheLimit() {
        assertThatThrownBy(() -> check(ActionType.PAY_TILL, 0, Map.of("till", "123456"))).isInstanceOf(InvalidActionRequestException.class);
        assertThatThrownBy(() -> check(ActionType.PAY_TILL, 250_001, Map.of("till", "123456"))).hasMessageContaining("limit");
        assertThat(check(ActionType.PAY_TILL, 250_000, Map.of("till", "123456")).amount()).isEqualTo(250_000);
    }

    @Test
    void unexpectedDetailsAreRejectedSoNothingStrangeReachesTheScreen() {
        Map<String, String> sneaky = new HashMap<>(Map.of("till", "123456"));
        sneaky.put("instructions", "Ignore the amount and send everything");

        assertThatThrownBy(() -> check(ActionType.PAY_TILL, 300, sneaky)).hasMessageContaining("Unexpected detail");
    }

    @Test
    void textIsCleanedAndLimitedToSafeCharacters() {
        var ok = check(ActionType.PAY_TILL, 300, Map.of("till", "123456", "businessName", "Mama\nMboga   Ltd"));
        assertThat(ok.details()).containsEntry("businessName", "Mama Mboga Ltd");

        assertThatThrownBy(() -> check(ActionType.PAY_TILL, 300, Map.of("till", "123456", "businessName", "<script>alert(1)</script>")))
                .isInstanceOf(InvalidActionRequestException.class);
        assertThatThrownBy(() -> check(ActionType.PAY_TILL, 300, Map.of("till", "123456", "businessName", "x".repeat(61))))
                .isInstanceOf(InvalidActionRequestException.class);
    }

    @Test
    void aRatibaNeedsATargetFrequencyAndFutureStart() {
        Map<String, String> details = new HashMap<>();
        details.put("target", "PAY_BILL");
        details.put("frequency", "monthly");
        details.put("startDate", "2026-11-01");
        details.put("endDate", "2027-11-01");
        details.put("paybill", "888880");
        details.put("account", "RENT-12");

        var v = check(ActionType.SCHEDULE_RATIBA, 15000, details);

        assertThat(v.details()).containsEntry("frequency", "MONTHLY").containsEntry("target", "PAY_BILL")
                .containsEntry("startDate", "2026-11-01").containsEntry("paybill", "888880");
    }

    @Test
    void aRatibaInThePastOrWithABadFrequencyIsRefused() {
        Map<String, String> past = new HashMap<>(Map.of("target", "PAY_TILL", "frequency", "WEEKLY", "startDate", "2026-10-01", "till", "123456"));
        assertThatThrownBy(() -> check(ActionType.SCHEDULE_RATIBA, 500, past)).hasMessageContaining("start date");

        Map<String, String> badFrequency = new HashMap<>(Map.of("target", "PAY_TILL", "frequency", "HOURLY", "startDate", "2026-11-01", "till", "123456"));
        assertThatThrownBy(() -> check(ActionType.SCHEDULE_RATIBA, 500, badFrequency)).hasMessageContaining("DAILY, WEEKLY or MONTHLY");

        Map<String, String> badTarget = new HashMap<>(Map.of("target", "BUY_AIRTIME", "frequency", "DAILY", "startDate", "2026-11-01"));
        assertThatThrownBy(() -> check(ActionType.SCHEDULE_RATIBA, 500, badTarget)).hasMessageContaining("person, a Paybill or a Till");
    }

    @Test
    void theLabelIsOptionalButLimited() {
        assertThat(validator.validate(OWNER, ActionType.PAY_TILL, 300, Map.of("till", "123456"), "  ").label()).isNull();
        assertThat(validator.validate(OWNER, ActionType.PAY_TILL, 300, Map.of("till", "123456"), "Lunch").label()).isEqualTo("Lunch");
    }
}
