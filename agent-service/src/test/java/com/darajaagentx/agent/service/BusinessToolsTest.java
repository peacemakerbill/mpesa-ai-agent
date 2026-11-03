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
        tools = new BusinessTools(api, "https://app.example.test/");
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
    void theToolsAreDefinedAndRecognised() {
        assertThat(BusinessTools.definitions()).extracting(d -> d.get("name"))
                .containsExactly("list_business_accounts", "prepare_business_request", "list_business_requests", "cancel_business_request");
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
    void moneyOutIsOnlyPreparedAndTheOwnerGetsAnApprovalLink() {
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
        verify(sender).text(eq(PHONE), text.capture());
        assertThat(text.getValue()).contains("https://app.example.test/b/" + ID);
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
        api.next = Map.of("id", ID, "status", "SUCCEEDED", "summary", "QR", "accountLabel", "Main shop", "qrCode", Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}));
        ToolContext c = ctx("TEXT");
        Result r = tools.execute(c, "prepare_business_request", json("{\"type\":\"DYNAMIC_QR\",\"amount\":300}"));
        assertThat(r.content()).contains("QR code is being sent");
        flush(c);
        verify(sender).image(eq(PHONE), org.mockito.ArgumentMatchers.any(byte[].class), eq("image/png"), contains("Main shop"));
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
}
