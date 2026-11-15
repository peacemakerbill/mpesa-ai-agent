package com.darajaagentx.agent.service;

import com.darajaagentx.agent.client.BusinessApi;
import com.darajaagentx.agent.model.Identity;
import com.darajaagentx.agent.model.Role;
import com.darajaagentx.agent.service.ToolExecutor.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** What the assistant can do with a customer's business accounts, and what it can never do. */
@ExtendWith(MockitoExtension.class)
class BusinessToolsTest {

    private static final String PHONE = "254712345678";
    private static final String ID = "11111111-2222-3333-4444-555555555555";

    @Mock ReplySender sender;

    private final FakeApi api = new FakeApi();
    private BusinessTools tools;

    /** Remembers what it was asked and answers with whatever the test sets. */
    static class FakeApi implements BusinessApi {
        List<Map<String, Object>> accounts = new ArrayList<>();
        List<Map<String, Object>> ops = new ArrayList<>();
        Map<String, Object> next = Map.of();
        Map<String, Object> lastBody;
        RuntimeException failure;
        String cancelled;

        @Override
        public List<Map<String, Object>> accounts(String phone) {
            return accounts;
        }

        @Override
        public Map<String, Object> prepare(Map<String, Object> body) {
            if (failure != null) {
                throw failure;
            }
            lastBody = body;
            return next;
        }

        @Override
        public List<Map<String, Object>> operations(String phone, int limit) {
            return ops;
        }

        @Override
        public Map<String, Object> operation(String id, String phone) {
            return ops.stream().filter(o -> o.get("id").equals(id)).findFirst().orElseThrow(() -> new BusinessApiException(404, "That request was not found."));
        }

        @Override
        public Map<String, Object> cancel(String id, String phone) {
            cancelled = id;
            return Map.of("summary", "Pay KES 500");
        }
    }

    @BeforeEach
    void setUp() {
        tools = new BusinessTools(api);
    }

    private static ToolContext ctx(String source) {
        return new ToolContext(new Identity(PHONE, Role.CUSTOMER, null), "wamid.42", source);
    }

    private static JsonNode json(String text) {
        return JsonMapper.builder().build().readTree(text);
    }

    private void flush(ToolContext ctx) {
        ctx.deferred().forEach(d -> d.accept(sender));
    }

    @Test
    void stkPushToTheCustomersOwnPhoneUsesTheRealNumberTheModelCannotSee() {
        api.next = Map.of("id", ID, "status", "SUBMITTED", "summary", "STK Push of KES 2 to 254712345678", "resultText", "Prompt sent.");

        Result r = tools.execute(ctx("TEXT"), "prepare_business_request",
                json("{\"account\":\"Daraja STK\",\"type\":\"STK_PUSH\",\"amount\":2,\"use_my_number\":true}"));

        assertThat(r.error()).isFalse();
        assertThat(((Map<?, ?>) api.lastBody.get("params")).get("phone")).isEqualTo(PHONE);
        assertThat(((Map<?, ?>) api.lastBody.get("params")).get("amount")).isEqualTo("2");
        assertThat(api.lastBody.get("account")).isEqualTo("Daraja STK");
    }

    @Test
    void wordsLikeMyPhoneInThePhoneFieldAreTheSameAsUseMyNumber() {
        api.next = Map.of("id", ID, "status", "SUBMITTED", "summary", "STK Push", "resultText", "Prompt sent.");

        tools.execute(ctx("TEXT"), "prepare_business_request", json("{\"type\":\"STK_PUSH\",\"amount\":2,\"phone\":\"my phone number\"}"));

        assertThat(((Map<?, ?>) api.lastBody.get("params")).get("phone")).isEqualTo(PHONE);
    }

    @Test
    void aMaskedNumberIsRefusedInsteadOfGuessedAndAFullOneIsKept() {
        Result masked = tools.execute(ctx("TEXT"), "prepare_business_request", json("{\"type\":\"B2C\",\"amount\":50,\"phone\":\"254723***340\"}"));
        assertThat(masked.error()).isTrue();
        assertThat(masked.content()).contains("only partly shown");
        assertThat(api.lastBody).isNull();

        api.next = Map.of("id", ID, "status", "PENDING_APPROVAL", "summary", "Pay KES 50");
        tools.execute(ctx("TEXT"), "prepare_business_request", json("{\"type\":\"B2C\",\"amount\":50,\"phone\":\"0723079340\"}"));
        assertThat(((Map<?, ?>) api.lastBody.get("params")).get("phone")).isEqualTo("0723079340");
    }

    @Test
    void theToolsAreDefinedAndRecognised() {
        assertThat(BusinessTools.definitions()).extracting(d -> d.get("name"))
                .containsExactly("list_business_accounts", "prepare_business_request", "list_business_requests", "lipa_na_bonga_steps", "cancel_business_request");
        assertThat(tools.handles("lipa_na_bonga_steps")).isTrue();
        assertThat(tools.handles("prepare_business_request")).isTrue();
        assertThat(tools.handles("prepare_send_money")).isFalse();
    }

    @Test
    void accountsAreListedByNameAndAbilityOnly() {
        assertThat(tools.execute(ctx("TEXT"), "list_business_accounts", json("{}")).content()).contains("Settings");
        api.accounts = List.of(Map.of("label", "Main shop", "environment", "SANDBOX", "shortcodeType", "BUY_GOODS", "shortcode", "174379",
                "capabilities", List.of(Map.of("code", "STK_PUSH", "label", "STK Push", "ready", true, "needs", ""),
                        Map.of("code", "B2C", "label", "B2C payouts", "ready", false, "needs", "initiator password"))));
        Result r = tools.execute(ctx("TEXT"), "list_business_accounts", json("{}"));
        assertThat(r.content()).contains("Main shop").contains("can do: STK Push").contains("B2C payouts needs initiator password");
    }

    @Test
    void moneyOutIsOnlyPreparedAndTheOwnerGetsAnOpenRequestMessage() {
        api.next = Map.of("id", ID, "status", "PENDING_APPROVAL", "summary", "Pay KES 500 to 254700111222 from Main shop");
        ToolContext c = ctx("TEXT");
        Result r = tools.execute(c, "prepare_business_request", json("{\"account\":\"@Main shop\",\"type\":\"b2c\",\"amount\":500,\"phone\":\"0700111222\"}"));
        assertThat(r.error()).isFalse();
        assertThat(r.content()).contains("waiting for the owner's approval").contains("Do not say that any money was sent");
        assertThat(api.lastBody.get("account")).isEqualTo("Main shop");
        assertThat(api.lastBody.get("type")).isEqualTo("B2C");
        assertThat(api.lastBody.get("phone")).isEqualTo(PHONE);
        assertThat(((Map<?, ?>) api.lastBody.get("params")).get("amount")).isEqualTo("500");
        flush(c);
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(sender).openBusinessRequest(eq(PHONE), text.capture(), eq(ID));
        assertThat(text.getValue()).contains("Tap *Open request*").doesNotContain("http").doesNotContain("/b/");
    }

    @Test
    void theSamePromptTwiceIsTheSameRequest() {
        api.next = Map.of("id", ID, "status", "PENDING_APPROVAL", "summary", "x");
        String body = "{\"account\":\"Main shop\",\"type\":\"B2C\",\"amount\":500,\"phone\":\"0700111222\"}";
        tools.execute(ctx("TEXT"), "prepare_business_request", json(body));
        Object first = api.lastBody.get("idempotencyKey");
        tools.execute(ctx("TEXT"), "prepare_business_request", json(body));
        assertThat(api.lastBody.get("idempotencyKey")).isEqualTo(first);
        tools.execute(ctx("TEXT"), "prepare_business_request", json(body.replace("500", "501")));
        assertThat(api.lastBody.get("idempotencyKey")).isNotEqualTo(first);
    }

    @Test
    void detailsFromVoiceOrPicturesAreMarkedAsRisky() {
        api.next = Map.of("id", ID, "status", "PENDING_APPROVAL", "summary", "x");
        tools.execute(ctx("VOICE"), "prepare_business_request", json("{\"type\":\"BALANCE\"}"));
        assertThat(api.lastBody.get("source")).isEqualTo("VOICE");
        tools.execute(ctx("TEXT"), "prepare_business_request", json("{\"type\":\"BALANCE\",\"details_from\":\"IMAGE\"}"));
        assertThat(api.lastBody.get("source")).isEqualTo("IMAGE");
    }

    @Test
    void aSubmittedRequestIsNotReportedAsDone() {
        api.next = Map.of("id", ID, "status", "SUBMITTED", "summary", "STK Push of KES 100", "resultText", "The prompt was sent.");
        Result r = tools.execute(ctx("TEXT"), "prepare_business_request", json("{\"type\":\"STK_PUSH\",\"amount\":\"100\",\"phone\":\"0712345678\"}"));
        assertThat(r.error()).isFalse();
        assertThat(r.content()).contains("arrives in this chat").contains("do not say it succeeded");
    }

    @Test
    void aQrCodeIsSentAsAPicture() {
        api.next = Map.of("id", ID, "status", "SUCCEEDED", "summary", "QR code for KES 300 to Main shop", "accountLabel", "Main shop", "qrCode", Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}));
        ToolContext c = ctx("TEXT");
        Result r = tools.execute(c, "prepare_business_request", json("{\"type\":\"DYNAMIC_QR\",\"amount\":300}"));
        assertThat(r.content()).contains("QR code is being sent");
        flush(c);
        verify(sender).image(eq(PHONE), org.mockito.ArgumentMatchers.any(byte[].class), eq("image/png"), contains("Main shop"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aQrCodeCanBeMadeForAnyTillPaybillOrPhoneAndTheCaptionSaysWhoIsPaid() {
        api.next = Map.of("id", ID, "status", "SUCCEEDED", "summary", "QR code for KES 200 to Till 123456 (reference Main)", "accountLabel", "Main shop",
                "params", Map.of("trxCode", "BG", "cpi", "123456"), "qrCode", Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}));
        ToolContext c = ctx("TEXT");
        Result r = tools.execute(c, "prepare_business_request", json("{\"type\":\"DYNAMIC_QR\",\"amount\":200,\"pay_to_type\":\"till\",\"pay_to_number\":\"123456\"}"));
        Map<String, Object> params = (Map<String, Object>) api.lastBody.get("params");
        assertThat(params).containsEntry("pay_to_type", "till").containsEntry("pay_to_number", "123456").containsEntry("amount", "200");
        assertThat(r.content()).contains("Till 123456").contains("lipa_na_bonga_steps").contains("not kept");
        flush(c);
        verify(sender).image(eq(PHONE), org.mockito.ArgumentMatchers.any(byte[].class), eq("image/png"), contains("Till 123456"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aQrCodeToTheCustomersOwnPhoneUsesTheRealNumberAndAMaskedOneIsRefused() {
        api.next = Map.of("id", ID, "status", "FAILED", "summary", "x", "failure", "stop here");
        tools.execute(ctx("TEXT"), "prepare_business_request", json("{\"type\":\"DYNAMIC_QR\",\"amount\":50,\"pay_to_type\":\"phone\",\"use_my_number\":true}"));
        assertThat(((Map<String, Object>) api.lastBody.get("params")).get("pay_to_number")).isEqualTo(PHONE);

        api.lastBody = null;
        Result masked = tools.execute(ctx("TEXT"), "prepare_business_request", json("{\"type\":\"DYNAMIC_QR\",\"amount\":50,\"pay_to_type\":\"phone\",\"pay_to_number\":\"254723***340\"}"));
        assertThat(masked.error()).isTrue();
        assertThat(masked.content()).contains("only partly shown");
        assertThat(api.lastBody).isNull();
    }

    @Test
    void lipaNaBongaStepsAreGivenForATillAndNeverPretendToChargeBongaPoints() {
        Result r = tools.execute(ctx("TEXT"), "lipa_na_bonga_steps", json("{\"till\":\"123 456\",\"amount\":200}"));
        assertThat(r.error()).isFalse();
        assertThat(r.content()).contains("*126#").contains("Lipa na Bonga").contains("123456").contains("1000 Bonga Points")
                .contains("no way for this assistant to charge Bonga Points").contains("never type that PIN in this chat");
        assertThat(api.lastBody).isNull();
    }

    @Test
    void lipaNaBongaFindsTheTillOfASavedBuyGoodsAccountAndWarnsAboutAPaybill() {
        api.accounts = List.of(
                Map.of("label", "Main shop", "shortcodeType", "BUY_GOODS", "shortcode", "174379", "tillNumber", "9876543"),
                Map.of("label", "Rent", "shortcodeType", "PAYBILL", "shortcode", "600111"));
        assertThat(tools.execute(ctx("TEXT"), "lipa_na_bonga_steps", json("{\"account\":\"@Main shop\"}")).content()).contains("9876543").contains("(Main shop)");
        assertThat(tools.execute(ctx("TEXT"), "lipa_na_bonga_steps", json("{\"account\":\"Rent\"}")).content()).contains("is a Paybill").contains("do not promise");
        Result none = tools.execute(ctx("TEXT"), "lipa_na_bonga_steps", json("{\"till\":\"12\"}"));
        assertThat(none.error()).isTrue();
        assertThat(none.content()).contains("Which Till");
    }

    @Test
    void failuresAndUnclearAnswersAreExplainedHonestly() {
        api.next = Map.of("id", ID, "status", "FAILED", "summary", "x", "failure", "Invalid Access Token");
        Result failed = tools.execute(ctx("TEXT"), "prepare_business_request", json("{\"type\":\"BALANCE\"}"));
        assertThat(failed.error()).isTrue();
        assertThat(failed.content()).contains("Invalid Access Token").contains("Nothing was sent");

        api.next = Map.of("id", ID, "status", "UNKNOWN", "summary", "x", "failure", "It may or may not have gone through.");
        Result unknown = tools.execute(ctx("TEXT"), "prepare_business_request", json("{\"type\":\"BALANCE\"}"));
        assertThat(unknown.error()).isFalse();
        assertThat(unknown.content()).contains("Do not try again").contains("statement");
    }

    @Test
    void anUnknownTypeIsRefusedBeforeAnythingIsAsked() {
        Result r = tools.execute(ctx("TEXT"), "prepare_business_request", json("{\"type\":\"LAUNDER\"}"));
        assertThat(r.error()).isTrue();
        assertThat(api.lastBody).isNull();
    }

    @Test
    void whenAgentXApiIsDownTheMessageIsFriendlyAndWhenItRefusesTheReasonIsShown() {
        api.failure = new BusinessApi.BusinessApiException(0, "not reachable");
        assertThat(tools.execute(ctx("TEXT"), "prepare_business_request", json("{\"type\":\"BALANCE\"}")).content()).contains("not available right now");
        api.failure = new BusinessApi.BusinessApiException(400, "\"Bare\" cannot do B2C yet. It needs the initiator name and password.");
        assertThat(tools.execute(ctx("TEXT"), "prepare_business_request", json("{\"type\":\"B2C\",\"amount\":5}")).content()).contains("cannot do B2C yet");
    }

    @Test
    void requestsCanBeListedReadInFullAndCancelled() {
        api.ops = new ArrayList<>(List.of(
                Map.of("id", ID, "status", "PENDING_APPROVAL", "summary", "Pay KES 500"),
                Map.of("id", "99999999-2222-3333-4444-555555555555", "status", "SUCCEEDED", "summary", "Balance", "resultText", "Working Account: KES 5.00")));
        assertThat(tools.execute(ctx("TEXT"), "list_business_requests", json("{}")).content()).contains("11111111 | PENDING_APPROVAL").contains("99999999 | SUCCEEDED");
        assertThat(tools.execute(ctx("TEXT"), "list_business_requests", json("{\"request_id\":\"99999999\"}")).content()).contains("Working Account: KES 5.00");
        assertThat(tools.execute(ctx("TEXT"), "cancel_business_request", json("{}")).content()).startsWith("Cancelled");
        assertThat(api.cancelled).isEqualTo(ID);
        assertThat(tools.execute(ctx("TEXT"), "cancel_business_request", json("{\"request_id\":\"zzzz\"}")).error()).isTrue();
        api.ops = new ArrayList<>();
        assertThat(tools.execute(ctx("TEXT"), "cancel_business_request", json("{}")).content()).contains("no business request waiting");
        verifyNoInteractions(sender);
    }

    @Test
    void aRequestIsExplainedStepByStepAndCanBeFoundByItsMpesaCode() {
        api.ops = new ArrayList<>(List.of(Map.of("id", ID, "status", "SUCCEEDED", "summary", "Pay KES 500 to 254700111222", "receipt", "QGH7ABC123",
                "resultText", "Done. KES 500 received by JOHN DOE.",
                "timeline", List.of(
                        Map.of("at", "2026-11-03T06:00:00Z", "code", "ASKED", "text", "Asked in chat: Pay KES 500 to 254700111222"),
                        Map.of("at", "2026-11-03T06:01:00Z", "code", "APPROVED", "text", "You approved it (biometric)."),
                        Map.of("at", "2026-11-03T06:03:30Z", "code", "SUCCEEDED", "text", "Done. KES 500 received by JOHN DOE.")))));

        String byCode = tools.execute(ctx("TEXT"), "list_business_requests", json("{\"request_id\":\"qgh7abc123\"}")).content();
        assertThat(byCode).contains("What happened, oldest first").contains("03 Nov 09:00 Asked in chat").contains("03 Nov 09:01 You approved it")
                .contains("03 Nov 09:03 Done. KES 500 received by JOHN DOE.");
        // the list shows one line per request, the steps only appear when one is opened
        assertThat(tools.execute(ctx("TEXT"), "list_business_requests", json("{}")).content()).doesNotContain("What happened");
        assertThat(BusinessTools.timeline(null)).isEmpty();
        assertThat(BusinessTools.timeline(List.of(Map.of("text", "No time given")))).isEqualTo("  - No time given");
    }
}
