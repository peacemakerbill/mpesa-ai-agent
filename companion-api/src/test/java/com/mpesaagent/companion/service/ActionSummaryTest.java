package com.mpesaagent.companion.service;

import com.mpesaagent.companion.model.ActionSource;
import com.mpesaagent.companion.model.ActionStatus;
import com.mpesaagent.companion.model.ActionType;
import com.mpesaagent.companion.model.WalletAction;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActionSummaryTest {

    private static WalletAction action(ActionType type, int amount, ActionSource source, Map<String, String> details) {
        return new WalletAction("key", "254700000001", type, amount, null, source, details, TestSupport.NOW);
    }

    @Test
    void sendMoneyMasksTheNumber() {
        String s = ActionSummary.summary(action(ActionType.SEND_MONEY, 1500, ActionSource.TEXT,
                Map.of("recipientPhone", "254712345678", "recipientName", "John")));

        assertThat(s).isEqualTo("Send KES 1,500 to John 254712***678");
    }

    @Test
    void contactNamesAreExplained() {
        assertThat(ActionSummary.summary(action(ActionType.SEND_MONEY, 200, ActionSource.CONTACT, Map.of("contactName", "Mama"))))
                .contains("Mama").contains("contact on your phone");
    }

    @Test
    void billsTillsPochiAndAirtime() {
        assertThat(ActionSummary.summary(action(ActionType.PAY_BILL, 800, ActionSource.TEXT, Map.of("paybill", "888880", "account", "123"))))
                .isEqualTo("Pay KES 800 to Paybill 888880, account 123");
        assertThat(ActionSummary.summary(action(ActionType.PAY_TILL, 300, ActionSource.TEXT, Map.of("till", "123456", "businessName", "Mama Mboga"))))
                .isEqualTo("Pay KES 300 to Till 123456 (Mama Mboga)");
        assertThat(ActionSummary.summary(action(ActionType.PAY_POCHI, 100, ActionSource.TEXT, Map.of("pochiPhone", "254722000111"))))
                .contains("Pochi la Biashara 254722***111");
        assertThat(ActionSummary.summary(action(ActionType.BUY_AIRTIME, 50, ActionSource.TEXT, Map.of())))
                .isEqualTo("Buy KES 50 airtime for your own number");
    }

    @Test
    void ratibaDescribesTheScheduleAndTheTarget() {
        String s = ActionSummary.summary(action(ActionType.SCHEDULE_RATIBA, 15000, ActionSource.TEXT,
                Map.of("target", "PAY_BILL", "frequency", "MONTHLY", "startDate", "2026-11-01", "paybill", "888880", "account", "RENT")));

        assertThat(s).contains("monthly Ratiba of KES 15,000").contains("2026-11-01").contains("Paybill 888880, account RENT");
    }

    @Test
    void detailsFromImagesAndVoiceCarryAWarning() {
        var image = ActionSummary.warnings(action(ActionType.PAY_TILL, 300, ActionSource.IMAGE, Map.of("till", "123456")));
        var voice = ActionSummary.warnings(action(ActionType.PAY_TILL, 300, ActionSource.VOICE, Map.of("till", "123456")));
        var typed = ActionSummary.warnings(action(ActionType.PAY_TILL, 300, ActionSource.TEXT, Map.of("till", "123456")));

        assertThat(image).anyMatch(w -> w.contains("image or a file"));
        assertThat(voice).anyMatch(w -> w.contains("misheard"));
        assertThat(typed).noneMatch(w -> w.contains("misheard") || w.contains("image or a file"));
    }

    @Test
    void everyRequestRemindsThePersonNeverToShareThePin() {
        for (ActionType type : ActionType.values()) {
            var w = ActionSummary.warnings(action(type, 100, ActionSource.TEXT, Map.of()));
            assertThat(w).anyMatch(x -> x.contains("PIN"));
        }
    }

    @Test
    void statusRulesAllowOnlyTheExpectedMoves() {
        assertThat(ActionStatus.CREATED.canMoveTo(ActionStatus.APPROVED)).isTrue();
        assertThat(ActionStatus.CREATED.canMoveTo(ActionStatus.HANDED_OFF)).isFalse();
        assertThat(ActionStatus.APPROVED.canMoveTo(ActionStatus.HANDED_OFF)).isTrue();
        assertThat(ActionStatus.HANDED_OFF.canMoveTo(ActionStatus.CANCELLED)).isFalse();
        assertThat(ActionStatus.HANDED_OFF.canMoveTo(ActionStatus.DONE_REPORTED)).isTrue();
        assertThat(ActionStatus.DONE_REPORTED.canMoveTo(ActionStatus.NOT_DONE)).isFalse();
        assertThat(ActionStatus.EXPIRED.isOpen()).isFalse();
        assertThat(ActionStatus.HANDED_OFF.canExpire()).isFalse();
    }
}
