package com.mpesaagent.agent.service;

import com.mpesaagent.agent.TestSupport;
import com.mpesaagent.agent.client.CompanionClient;
import com.mpesaagent.agent.chat.ChatChannel.Button;
import com.mpesaagent.agent.config.ServiceProperties;
import com.mpesaagent.agent.dto.WalletActionView;
import com.mpesaagent.agent.exception.CompanionApiException;
import com.mpesaagent.agent.model.Identity;
import com.mpesaagent.agent.model.Role;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletToolsTest {

    private static final String PHONE = "254700000001";
    private static final UUID ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Mock CompanionClient companion;
    @Mock ReplySender sender;

    private ToolExecutor executor;

    @BeforeEach
    void setUp() {
        WalletTools wallet = new WalletTools(companion, new ServiceProperties.Companion("http://companion", "https://app.example.test/"));
        executor = new ToolExecutor(null, null, sender, TestSupport.walletProps(), TestSupport.business(), wallet);
    }

    private static ToolContext ctx(String source) {
        return new ToolContext(new Identity(PHONE, Role.CUSTOMER, null), "wamid.42", source);
    }

    private static JsonNode json(String text) {
        try {
            return TestSupport.mapper().readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static WalletActionView view(String status, String summary, boolean needsContact, String receipt, int amount) {
        return new WalletActionView(ID, "PAY_BILL", status, amount, null, "TEXT", summary, List.of(), needsContact, receipt, null,
                Instant.now(), Instant.now());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sentBody() {
        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
        verify(companion).createAction(body.capture());
        return body.getValue();
    }

    @SuppressWarnings("unchecked")
	@Test
    void preparingABillSendsOnlyTheSenderNumberAndQueuesTheLinkAndCancelButton() {
        when(companion.createAction(any())).thenReturn(view("CREATED", "Pay KES 1,200 to Paybill 888880, account 123", false, null, 1200));
        ToolContext ctx = ctx("TEXT");

        ToolExecutor.Result result = executor.execute(ctx, ToolCatalog.PREPARE_PAY_BILL,
                json("{\"amount\":1200,\"paybill\":\"888880\",\"account\":\"123\",\"phone\":\"254799999999\"}"));

        assertThat(result.error()).isFalse();
        assertThat(result.content()).contains("Do not say that any money was sent");
        Map<String, Object> body = sentBody();
        // the chat's own number is used, whatever the model put in the input
        assertThat(body).containsEntry("phone", PHONE).containsEntry("type", "PAY_BILL").containsEntry("amount", 1200)
                .containsEntry("source", "TEXT");
        assertThat((Map<String, String>) body.get("details")).containsOnlyKeys("paybill", "account")
                .containsEntry("paybill", "888880");

        ctx.deferred().forEach(a -> a.accept(sender));
        verify(sender).text(eq(PHONE), contains("https://app.example.test/a/" + ID));
        ArgumentCaptor<List<Button>> buttons = ArgumentCaptor.forClass((Class<List<Button>>) (Class<?>) List.class);
        verify(sender).buttons(eq(PHONE), anyString(), isNull(), buttons.capture());
        assertThat(buttons.getValue()).extracting(Button::id).containsExactly("WCAN:" + ID);
    }

    @Test
    void nothingIsQueuedWhenCompanionApiRefuses() {
        when(companion.createAction(any())).thenThrow(new CompanionApiException(400, "The paybill number must be 5 to 7 digits"));
        ToolContext ctx = ctx("TEXT");

        ToolExecutor.Result result = executor.execute(ctx, ToolCatalog.PREPARE_PAY_BILL,
                json("{\"amount\":1200,\"paybill\":\"12\",\"account\":\"x\"}"));

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("5 to 7 digits");
        assertThat(ctx.deferred()).isEmpty();
    }

    @Test
    void detailsFromAPictureAreMarkedAsRiskyEvenIfTheModelSaysTyped() {
        when(companion.createAction(any())).thenReturn(view("CREATED", "Pay KES 300 to Till 123456", false, null, 300));

        ToolExecutor.Result result = executor.execute(ctx("IMAGE"), ToolCatalog.PREPARE_PAY_TILL,
                json("{\"amount\":300,\"till\":\"123456\",\"details_from\":\"TEXT\"}"));

        assertThat(sentBody()).containsEntry("source", "IMAGE");
        assertThat(result.content()).contains("check every number");
    }

    @Test
    void theModelCanRaiseButNotLowerTheRisk() {
        assertThat(WalletTools.riskier("TEXT", "VOICE")).isEqualTo("VOICE");
        assertThat(WalletTools.riskier("IMAGE", "TEXT")).isEqualTo("IMAGE");
        assertThat(WalletTools.riskier("VOICE", "CONTACT")).isEqualTo("VOICE");
        assertThat(WalletTools.riskier("TEXT", "nonsense")).isEqualTo("TEXT");
        assertThat(WalletTools.riskier(null, null)).isEqualTo("TEXT");
    }

    @SuppressWarnings("unchecked")
	@Test
    void sendingToAContactNameKeepsTheNameOnly() {
        when(companion.createAction(any())).thenReturn(view("CREATED", "Send KES 500 to Mama (a contact on your phone)", true, null, 500));
        ToolContext ctx = ctx("TEXT");

        executor.execute(ctx, ToolCatalog.PREPARE_SEND_MONEY, json("{\"amount\":500,\"contact_name\":\"Mama\"}"));

        assertThat((Map<String, String>) sentBody().get("details")).containsOnlyKeys("contactName").containsEntry("contactName", "Mama");
        ctx.deferred().forEach(a -> a.accept(sender));
        verify(sender).text(eq(PHONE), contains("pick the contact on your phone"));
    }

    @SuppressWarnings("unchecked")
	@Test
    void ratibaPassesTheScheduleThrough() {
        when(companion.createAction(any())).thenReturn(view("CREATED", "Ratiba", false, null, 15000));

        executor.execute(ctx("TEXT"), ToolCatalog.PREPARE_RATIBA, json("{\"amount\":15000,\"target\":\"PAY_BILL\",\"frequency\":\"MONTHLY\","
                + "\"start_date\":\"2026-11-01\",\"paybill\":\"888880\",\"account\":\"RENT\"}"));

        Map<String, Object> body = sentBody();
        assertThat(body).containsEntry("type", "SCHEDULE_RATIBA");
        assertThat((Map<String, String>) body.get("details")).containsEntry("target", "PAY_BILL").containsEntry("frequency", "MONTHLY")
                .containsEntry("startDate", "2026-11-01").containsEntry("paybill", "888880").containsEntry("account", "RENT");
    }

    @Test
    void badAmountsNeverReachCompanionApi() {
        ToolExecutor.Result result = executor.execute(ctx("TEXT"), ToolCatalog.PREPARE_PAY_TILL, json("{\"amount\":\"lots\",\"till\":\"123456\"}"));

        assertThat(result.error()).isTrue();
        verifyNoInteractions(companion);
    }

    @Test
    void theSameMessageAskingForTheSameThingGetsTheSameKey() {
        Map<String, String> details = Map.of("till", "123456");

        String a = WalletTools.idempotencyKey("wamid.1", "PAY_TILL", 300, details);
        String b = WalletTools.idempotencyKey("wamid.1", "PAY_TILL", 300, details);
        String otherAmount = WalletTools.idempotencyKey("wamid.1", "PAY_TILL", 301, details);
        String otherMessage = WalletTools.idempotencyKey("wamid.2", "PAY_TILL", 300, details);

        assertThat(a).isEqualTo(b).startsWith("wa-").hasSizeLessThanOrEqualTo(100);
        assertThat(otherAmount).isNotEqualTo(a);
        assertThat(otherMessage).isNotEqualTo(a);
    }

    @Test
    void listingShowsStatusAndSummary() {
        when(companion.list(PHONE, 5)).thenReturn(List.of(view("DONE_REPORTED", "Pay KES 300 to Till 123456", false, "SGL1234567", 300)));

        ToolExecutor.Result result = executor.execute(ctx("TEXT"), ToolCatalog.LIST_WALLET_REQUESTS, json("{}"));

        assertThat(result.content()).contains("DONE_REPORTED").contains("Till 123456").contains("SGL1234567").contains(ID.toString());
    }

    @Test
    void cancellingWithoutAnIdCancelsTheNewestWaitingRequest() {
        when(companion.list(PHONE, 10)).thenReturn(List.of(
                view("DONE_REPORTED", "old", false, null, 100), view("CREATED", "Pay KES 300 to Till 123456", false, null, 300)));
        when(companion.cancel(ID, PHONE)).thenReturn(view("CANCELLED", "Pay KES 300 to Till 123456", false, null, 300));

        ToolExecutor.Result result = executor.execute(ctx("TEXT"), ToolCatalog.CANCEL_WALLET_REQUEST, json("{}"));

        assertThat(result.content()).contains("Cancelled").contains("Nothing was sent");
        verify(companion).cancel(ID, PHONE);
    }

    @Test
    void cancellingWhenNothingIsWaiting() {
        when(companion.list(PHONE, 10)).thenReturn(List.of(view("DONE_REPORTED", "old", false, null, 100)));

        ToolExecutor.Result result = executor.execute(ctx("TEXT"), ToolCatalog.CANCEL_WALLET_REQUEST, json("{}"));

        assertThat(result.content()).contains("no waiting request");
        verify(companion, never()).cancel(any(), anyString());
    }

    @Test
    void aConfirmationCodeIsMatchedByAmountAndRecordedAsReported() {
        when(companion.list(PHONE, 10)).thenReturn(List.of(
                view("HANDED_OFF", "Pay KES 300 to Till 123456", false, null, 300),
                view("HANDED_OFF", "Pay KES 900 to Till 999999", false, null, 900)));
        when(companion.attachReceipt(ID, PHONE, "SGL1234567")).thenReturn(view("DONE_REPORTED", "Pay KES 300 to Till 123456", false, "SGL1234567", 300));

        ToolExecutor.Result result = executor.execute(ctx("IMAGE"), ToolCatalog.RECORD_CONFIRMATION,
                json("{\"receipt\":\"sgl1234567\",\"amount\":300}"));

        assertThat(result.error()).isFalse();
        assertThat(result.content()).contains("SGL1234567").contains("not been checked with Safaricom");
    }

    @Test
    void aConfirmationThatMatchesNothingIsNotRecorded() {
        when(companion.list(PHONE, 10)).thenReturn(List.of(view("CREATED", "Pay KES 300 to Till 123456", false, null, 300)));

        ToolExecutor.Result result = executor.execute(ctx("TEXT"), ToolCatalog.RECORD_CONFIRMATION, json("{\"receipt\":\"SGL1234567\"}"));

        assertThat(result.content()).contains("Nothing was recorded");
        verify(companion, never()).attachReceipt(any(), anyString(), anyString());
    }

    @Test
    void anInvalidConfirmationCodeIsRefused() {
        ToolExecutor.Result result = executor.execute(ctx("TEXT"), ToolCatalog.RECORD_CONFIRMATION, json("{\"receipt\":\"hello!\"}"));

        assertThat(result.error()).isTrue();
        verifyNoInteractions(companion);
    }

    @Test
    void findingAContactOnlyOpensTheFinderInTheApp() {
        ToolContext ctx = ctx("TEXT");

        ToolExecutor.Result result = executor.execute(ctx, ToolCatalog.FIND_CONTACT, json("{\"name\":\"Mama\"}"));

        assertThat(result.content()).contains("Mama").contains("tap the right person");
        verifyNoInteractions(companion);
        ctx.deferred().forEach(a -> a.accept(sender));
        verify(sender).contactLookup(PHONE, "Mama");
    }

    @Test
    void findingAContactNeedsAName() {
        ToolExecutor.Result result = executor.execute(ctx("TEXT"), ToolCatalog.FIND_CONTACT, json("{\"name\":\"  \"}"));

        assertThat(result.content()).contains("name");
        verifyNoInteractions(companion);
    }

    @Test
    void anOutageIsReportedPlainly() {
        when(companion.list(eq(PHONE), anyInt())).thenThrow(new CompanionApiException(0, "down"));

        ToolExecutor.Result result = executor.execute(ctx("TEXT"), ToolCatalog.LIST_WALLET_REQUESTS, json("{}"));

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("not available right now");
    }

    @Test
    void businessToolsAreRefusedWhenTheyAreSwitchedOff() {
        ToolExecutor.Result result = executor.execute(ctx("TEXT"), ToolCatalog.PROPOSE_PAYMENT, json("{\"amount\":100}"));

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("not available");
        verifyNoInteractions(companion);
    }

    @Test
    void theWalletToolListHasNoBusinessToolsAndEveryWalletToolHasASchema() {
        ToolCatalog catalog = new ToolCatalog(TestSupport.mapper(), TestSupport.walletProps());

        var names = new java.util.ArrayList<String>();
        catalog.toolsFor(Role.ADMIN).forEach(t -> {
            names.add(t.path("name").asString());
            assertThat(t.path("input_schema").path("type").asString()).isEqualTo("object");
        });

        assertThat(names).contains(ToolCatalog.PREPARE_SEND_MONEY, ToolCatalog.PREPARE_PAY_BILL, ToolCatalog.PREPARE_PAY_TILL,
                        ToolCatalog.PREPARE_PAY_POCHI, ToolCatalog.PREPARE_BUY_AIRTIME, ToolCatalog.PREPARE_RATIBA,
                        ToolCatalog.RECORD_CONFIRMATION, ToolCatalog.FIND_CONTACT, ToolCatalog.ESCALATE)
                .doesNotContainAnyElementsOf(ToolCatalog.BUSINESS_TOOLS);
        assertThat(names).doesNotHaveDuplicates();
    }

    @Test
    void withBusinessPaymentsOnBothSetsAreOfferedWithoutDuplicates() {
        ToolCatalog catalog = new ToolCatalog(TestSupport.mapper(), TestSupport.props());

        var names = new java.util.ArrayList<String>();
        catalog.toolsFor(Role.ADMIN).forEach(t -> names.add(t.path("name").asString()));

        assertThat(names).contains(ToolCatalog.PREPARE_PAY_BILL, ToolCatalog.PROPOSE_PAYMENT, ToolCatalog.REQUEST_REVERSAL);
        assertThat(names).doesNotHaveDuplicates();
    }

    @Test
    void sourceOfAMessageFollowsItsKind() {
        var audio = new com.mpesaagent.agent.dto.InboundMessage("m", PHONE, null, 0L, com.mpesaagent.agent.dto.InboundMessage.Kind.AUDIO,
                null, "x", "audio/ogg", null, null, true, null, null, null, null, null, null, null, null);
        assertThat(MessageProcessor.sourceOf(audio)).isEqualTo("VOICE");
        assertThat(MessageProcessor.sourceOf(com.mpesaagent.agent.dto.InboundMessage.text("m", PHONE, "hi"))).isEqualTo("TEXT");
    }
}