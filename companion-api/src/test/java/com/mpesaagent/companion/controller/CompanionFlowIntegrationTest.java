package com.mpesaagent.companion.controller;

import com.mpesaagent.companion.event.ActionEvent;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The whole journey with the real controllers, services and database. Only RabbitMQ is replaced. */
@SpringBootTest
@AutoConfigureMockMvc
class CompanionFlowIntegrationTest {

    private static final String KEY = "test-internal-key";
    private static final AtomicInteger NEXT_PHONE = new AtomicInteger(10_000_000);

    @Autowired MockMvc mvc;
    @Autowired JsonMapper mapper;
    @MockitoBean RabbitTemplate rabbit;

    /** A new customer number per test so tests do not see each other's data. */
    private static String newPhone() {
        return "2547" + NEXT_PHONE.incrementAndGet();
    }

    private ResultActions internalPost(String url, Object body) throws Exception {
        return mvc.perform(post(url).header("X-Internal-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)));
    }

    private DocumentContext json(ResultActions r) throws Exception {
        return JsonPath.parse(r.andReturn().getResponse().getContentAsString());
    }

    /** Link a phone the way a customer would: code from the agent, typed into the app. Returns the token. */
    private String link(String phone, String deviceId) throws Exception {
        DocumentContext code = json(internalPost("/internal/link-codes", Map.of("phone", phone)).andExpect(status().isOk()));
        Map<String, Object> body = new HashMap<>();
        body.put("phone", phone);
        body.put("code", code.read("$.code", String.class));
        body.put("deviceId", deviceId);
        body.put("deviceName", "Test phone");
        DocumentContext token = json(mvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body))).andExpect(status().isOk()));
        return token.read("$.token", String.class);
    }

    private UUID createBill(String phone, String key) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("idempotencyKey", key);
        body.put("phone", phone);
        body.put("type", "PAY_BILL");
        body.put("amount", 1200);
        body.put("source", "IMAGE");
        body.put("label", "Electricity");
        body.put("details", Map.of("paybill", "888880", "account", "123456"));
        DocumentContext created = json(internalPost("/internal/actions", body).andExpect(status().isOk()));
        return UUID.fromString(created.read("$.id", String.class));
    }

    private ResultActions app(String token, String method, String url, Object body) throws Exception {
        var builder = method.equals("GET") ? get(url) : post(url);
        builder.header("Authorization", "Bearer " + token);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        }
        return mvc.perform(builder);
    }

    @Test
    void fullJourneyFromChatRequestToReportedResult() throws Exception {
        String phone = newPhone();
        String token = link(phone, "device-one-12345");
        UUID id = createBill(phone, "wa-" + UUID.randomUUID());

        app(token, "GET", "/api/actions", null).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].status").value("CREATED"))
                .andExpect(jsonPath("$[0].summary").value("Pay KES 1,200 to Paybill 888880, account 123456"))
                .andExpect(jsonPath("$[0].warnings[0]").value(org.hamcrest.Matchers.containsString("image or a file")));

        app(token, "POST", "/api/actions/" + id + "/approve", Map.of("method", "BIOMETRIC"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        app(token, "POST", "/api/actions/" + id + "/handoff", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("HANDED_OFF"));
        // a retried request is not an error
        app(token, "POST", "/api/actions/" + id + "/handoff", null).andExpect(status().isOk());

        app(token, "POST", "/api/actions/" + id + "/report", Map.of("outcome", "DONE", "receipt", "sgl1234567"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE_REPORTED"))
                .andExpect(jsonPath("$.receipt").value("SGL1234567"));

        verify(rabbit).convertAndSend(eq("wallet.events"), eq("wallet.action"), any(ActionEvent.class));
    }

    @Test
    void anotherCustomerCannotSeeOrChangeSomeoneElsesRequest() throws Exception {
        String owner = newPhone();
        String stranger = newPhone();
        link(owner, "device-owner-1234");
        String strangerToken = link(stranger, "device-stranger-12");
        UUID id = createBill(owner, "wa-" + UUID.randomUUID());

        app(strangerToken, "GET", "/api/actions/" + id, null).andExpect(status().isNotFound());
        app(strangerToken, "POST", "/api/actions/" + id + "/approve", Map.of("method", "BIOMETRIC")).andExpect(status().isNotFound());
        app(strangerToken, "GET", "/api/actions", null).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/internal/actions/" + id).param("phone", stranger).header("X-Internal-Key", KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    void theStepsMustHappenInOrder() throws Exception {
        String phone = newPhone();
        String token = link(phone, "device-order-12345");
        UUID id = createBill(phone, "wa-" + UUID.randomUUID());

        // cannot open M-Pesa before approving
        app(token, "POST", "/api/actions/" + id + "/handoff", null).andExpect(status().isConflict());
        app(token, "POST", "/api/actions/" + id + "/decline", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DECLINED"));
        app(token, "POST", "/api/actions/" + id + "/approve", Map.of("method", "BIOMETRIC")).andExpect(status().isConflict());
        verify(rabbit).convertAndSend(eq("wallet.events"), eq("wallet.action"), any(ActionEvent.class));
    }

    @Test
    void cancellingFromTheChatStopsTheApp() throws Exception {
        String phone = newPhone();
        String token = link(phone, "device-cancel-1234");
        UUID id = createBill(phone, "wa-" + UUID.randomUUID());

        internalPost("/internal/actions/" + id + "/cancel", Map.of("phone", phone))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        app(token, "POST", "/api/actions/" + id + "/approve", Map.of("method", "BIOMETRIC")).andExpect(status().isConflict());
    }

    @Test
    void anActionCannotBeCancelledOnceM_PesaWasOpened() throws Exception {
        String phone = newPhone();
        String token = link(phone, "device-late-123456");
        UUID id = createBill(phone, "wa-" + UUID.randomUUID());
        app(token, "POST", "/api/actions/" + id + "/approve", Map.of("method", "DEVICE_CREDENTIAL")).andExpect(status().isOk());
        app(token, "POST", "/api/actions/" + id + "/handoff", null).andExpect(status().isOk());

        internalPost("/internal/actions/" + id + "/cancel", Map.of("phone", phone)).andExpect(status().isConflict());
    }

    @Test
    void sendingTheSameRequestTwiceGivesTheSameAction() throws Exception {
        String phone = newPhone();
        String key = "wa-" + UUID.randomUUID();

        UUID first = createBill(phone, key);
        UUID second = createBill(phone, key);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void reusingAKeyForADifferentRequestIsRefused() throws Exception {
        String phone = newPhone();
        String key = "wa-" + UUID.randomUUID();
        createBill(phone, key);

        Map<String, Object> other = new HashMap<>();
        other.put("idempotencyKey", key);
        other.put("phone", phone);
        other.put("type", "PAY_TILL");
        other.put("amount", 50);
        other.put("details", Map.of("till", "123456"));
        internalPost("/internal/actions", other).andExpect(status().isConflict());
    }

    @Test
    void badRequestDetailsAreRefusedBeforeAnythingIsStored() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("idempotencyKey", "wa-" + UUID.randomUUID());
        body.put("phone", newPhone());
        body.put("type", "PAY_BILL");
        body.put("amount", 100);
        body.put("details", Map.of("paybill", "12", "account", "x"));

        internalPost("/internal/actions", body).andExpect(status().isBadRequest());
    }

    @Test
    void theConfirmationCodeCanBeAddedFromTheChatAfterHandOff() throws Exception {
        String phone = newPhone();
        String token = link(phone, "device-receipt-123");
        UUID id = createBill(phone, "wa-" + UUID.randomUUID());
        app(token, "POST", "/api/actions/" + id + "/approve", Map.of("method", "BIOMETRIC")).andExpect(status().isOk());
        app(token, "POST", "/api/actions/" + id + "/handoff", null).andExpect(status().isOk());

        internalPost("/internal/actions/" + id + "/receipt", Map.of("phone", phone, "receipt", "SGL1234567"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE_REPORTED"))
                .andExpect(jsonPath("$.receipt").value("SGL1234567"));
        internalPost("/internal/actions/" + id + "/receipt", Map.of("phone", phone, "receipt", "ABCDEFGHIJ"))
                .andExpect(status().isConflict());
        internalPost("/internal/actions/" + id + "/receipt", Map.of("phone", phone, "receipt", "bad!"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aWrongCodeFiveTimesBurnsTheCode() throws Exception {
        String phone = newPhone();
        DocumentContext code = json(internalPost("/internal/link-codes", Map.of("phone", phone)).andExpect(status().isOk()));
        String real = code.read("$.code", String.class);
        String wrong = real.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            verifyCode(phone, wrong, "device-guess-12345").andExpect(status().isUnauthorized());
        }
        // even the right code no longer works
        verifyCode(phone, real, "device-guess-12345").andExpect(status().isUnauthorized());
    }

    @Test
    void theRightCodeWorksOnlyOnce() throws Exception {
        String phone = newPhone();
        DocumentContext code = json(internalPost("/internal/link-codes", Map.of("phone", phone)).andExpect(status().isOk()));

        verifyCode(phone, code.read("$.code", String.class), "device-once-123456").andExpect(status().isOk());
        verifyCode(phone, code.read("$.code", String.class), "device-once-123456").andExpect(status().isUnauthorized());
    }

    @Test
    void aNewCodeReplacesTheOldOne() throws Exception {
        String phone = newPhone();
        String first = json(internalPost("/internal/link-codes", Map.of("phone", phone))).read("$.code", String.class);
        String second = json(internalPost("/internal/link-codes", Map.of("phone", phone))).read("$.code", String.class);

        if (!first.equals(second)) {
            verifyCode(phone, first, "device-replace-1234").andExpect(status().isUnauthorized());
        }
        verifyCode(phone, second, "device-replace-1234").andExpect(status().isOk());
    }

    @Test
    void codesAreRateLimitedPerNumber() throws Exception {
        String phone = newPhone();
        for (int i = 0; i < 3; i++) {
            internalPost("/internal/link-codes", Map.of("phone", phone)).andExpect(status().isOk());
        }
        internalPost("/internal/link-codes", Map.of("phone", phone)).andExpect(status().isTooManyRequests());
    }

    private ResultActions verifyCode(String phone, String code, String deviceId) throws Exception {
        return mvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("phone", phone, "code", code, "deviceId", deviceId))));
    }

    @Test
    void signingOutStopsTheToken() throws Exception {
        String phone = newPhone();
        String token = link(phone, "device-logout-1234");
        app(token, "GET", "/api/actions", null).andExpect(status().isOk());

        mvc.perform(delete("/api/devices/me").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());

        app(token, "GET", "/api/actions", null).andExpect(status().isUnauthorized());
    }

    @Test
    void endpointsRefuseCallersWithoutCredentials() throws Exception {
        mvc.perform(get("/api/actions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/actions").header("Authorization", "Bearer not-a-token")).andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/link-codes").contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"0712345678\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/link-codes").header("X-Internal-Key", "wrong").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"0712345678\"}")).andExpect(status().isUnauthorized());
        verify(rabbit, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    void theAppReceivesTheHandOffConfig() throws Exception {
        String token = link(newPhone(), "device-config-1234");

        app(token, "GET", "/api/config", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.ussdMain").value("*334#"));
    }

    @Test
    void theLandingPageAndAppLinkFilesArePublic() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get("/a/" + id)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("mpesaagent://a/" + id)));
        mvc.perform(get("/.well-known/assetlinks.json")).andExpect(status().isNotFound());
        mvc.perform(get("/.well-known/apple-app-site-association")).andExpect(status().isNotFound());
        mvc.perform(get("/a/not-a-uuid")).andExpect(status().isBadRequest());
    }

    private Map<String, Object> smsBody(String code, String kind, int amount, String payee, String account, long at) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", code);
        m.put("kind", kind);
        m.put("amount", amount);
        if (payee != null) {
            m.put("payee", payee);
        }
        if (account != null) {
            m.put("account", account);
        }
        m.put("smsAt", at);
        return m;
    }

    private String handedOffBill(String phone, String deviceId) throws Exception {
        String token = link(phone, deviceId);
        UUID id = createBill(phone, "wa-" + UUID.randomUUID());
        app(token, "POST", "/api/actions/" + id + "/approve", Map.of("method", "BIOMETRIC")).andExpect(status().isOk());
        app(token, "POST", "/api/actions/" + id + "/handoff", null).andExpect(status().isOk());
        return token + "|" + id;
    }

    @Test
    void aMatchingMessageOnThePhoneCompletesTheRequestAndTellsTheChat() throws Exception {
        String phone = newPhone();
        String[] t = handedOffBill(phone, "device-sms-one-123").split("\\|");

        app(t[0], "POST", "/api/actions/" + t[1] + "/sms-evidence",
                smsBody("SGL1234567", "BILL", 1200, "KPLC PREPAID", "123456", System.currentTimeMillis()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE_REPORTED"))
                .andExpect(jsonPath("$.verification").value("SMS_MATCHED"))
                .andExpect(jsonPath("$.receipt").value("SGL1234567"));

        // the same message again changes nothing
        app(t[0], "POST", "/api/actions/" + t[1] + "/sms-evidence",
                smsBody("SGL1234567", "BILL", 1200, "KPLC PREPAID", "123456", System.currentTimeMillis()))
                .andExpect(status().isOk());
        verify(rabbit).convertAndSend(eq("wallet.events"), eq("wallet.action"), any(ActionEvent.class));
    }

    @Test
    void aMessageForADifferentAccountIsRefusedAndNothingChanges() throws Exception {
        String phone = newPhone();
        String[] t = handedOffBill(phone, "device-sms-two-123").split("\\|");

        app(t[0], "POST", "/api/actions/" + t[1] + "/sms-evidence",
                smsBody("SGL1234567", "BILL", 1200, "KPLC PREPAID", "999999", System.currentTimeMillis()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("different account")));
        app(t[0], "GET", "/api/actions/" + t[1], null)
                .andExpect(jsonPath("$.status").value("HANDED_OFF")).andExpect(jsonPath("$.verification").value("NONE"));
    }

    @Test
    void oneMessageCannotBackUpTwoRequests() throws Exception {
        String phone = newPhone();
        String token = link(phone, "device-sms-three-1");
        for (int i = 0; i < 2; i++) {
            UUID id = createBill(phone, "wa-" + UUID.randomUUID());
            app(token, "POST", "/api/actions/" + id + "/approve", Map.of("method", "BIOMETRIC")).andExpect(status().isOk());
            var result = app(token, "POST", "/api/actions/" + id + "/sms-evidence",
                    smsBody("QWE9876543", "BILL", 1200, null, "123456", System.currentTimeMillis()));
            if (i == 0) {
                result.andExpect(status().isOk());
            } else {
                result.andExpect(status().isConflict());
            }
        }
    }

    @Test
    void aMessageCanCorrectAWrongNotDoneReport() throws Exception {
        String phone = newPhone();
        String[] t = handedOffBill(phone, "device-sms-four-12").split("\\|");
        app(t[0], "POST", "/api/actions/" + t[1] + "/report", Map.of("outcome", "NOT_DONE")).andExpect(status().isOk());

        app(t[0], "POST", "/api/actions/" + t[1] + "/sms-evidence",
                smsBody("ZXC1112223", "BILL", 1200, null, "123456", System.currentTimeMillis()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE_REPORTED"));
    }

    @Test
    void aMessageIsRefusedBeforeApprovalAndForOtherCustomers() throws Exception {
        String phone = newPhone();
        String token = link(phone, "device-sms-five-12");
        UUID id = createBill(phone, "wa-" + UUID.randomUUID());
        Map<String, Object> body = smsBody("LKJ4445556", "BILL", 1200, null, "123456", System.currentTimeMillis());

        app(token, "POST", "/api/actions/" + id + "/sms-evidence", body).andExpect(status().isConflict());

        String other = newPhone();
        String otherToken = link(other, "device-sms-six-123");
        app(otherToken, "POST", "/api/actions/" + id + "/sms-evidence", body).andExpect(status().isNotFound());
    }

    @Test
    void badMessageFieldsAreRejected() throws Exception {
        String phone = newPhone();
        String[] t = handedOffBill(phone, "device-sms-seven-1").split("\\|");

        app(t[0], "POST", "/api/actions/" + t[1] + "/sms-evidence",
                smsBody("short", "BILL", 1200, null, "123456", System.currentTimeMillis())).andExpect(status().isBadRequest());
        app(t[0], "POST", "/api/actions/" + t[1] + "/sms-evidence",
                smsBody("SGL1234567", "BILL", 1200, "<script>", "123456", System.currentTimeMillis())).andExpect(status().isBadRequest());
    }
}