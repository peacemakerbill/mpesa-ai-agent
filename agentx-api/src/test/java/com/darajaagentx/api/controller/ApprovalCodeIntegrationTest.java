package com.darajaagentx.api.controller;

import com.darajaagentx.api.service.ApprovalCodeService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Approving a request with a code sent by text message, with the real controllers, services and database.
 * Codes are shown in the answer here (as on a test console), which is how the tests read them.
 */
@SpringBootTest(properties = {"agentx.code-delivery=log", "agentx.show-code-in-app=true"})
@AutoConfigureMockMvc
class ApprovalCodeIntegrationTest {

    private static final String KEY = "test-internal-key";
    private static final AtomicInteger NEXT_PHONE = new AtomicInteger(20_000_000);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean RabbitTemplate rabbit;

    private static String newPhone() {
        return "2547" + NEXT_PHONE.incrementAndGet();
    }

    private JsonNode json(ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private ResultActions internalPost(String url, Object body) throws Exception {
        return mvc.perform(post(url).header("X-Internal-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)));
    }

    private String link(String phone, String deviceId) throws Exception {
        JsonNode code = json(internalPost("/internal/link-codes", Map.of("phone", phone)).andExpect(status().isOk()));
        Map<String, Object> body = new HashMap<>();
        body.put("phone", phone);
        body.put("code", code.get("code").asString());
        body.put("deviceId", deviceId);
        body.put("deviceName", "Test phone");
        JsonNode token = json(mvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body))).andExpect(status().isOk()));
        return token.get("token").asString();
    }

    private UUID createBill(String phone) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("idempotencyKey", "otp-" + UUID.randomUUID());
        body.put("phone", phone);
        body.put("type", "PAY_BILL");
        body.put("amount", 1200);
        body.put("source", "TEXT");
        body.put("details", Map.of("paybill", "888880", "account", "123456"));
        JsonNode created = json(internalPost("/internal/actions", body).andExpect(status().isOk()));
        return UUID.fromString(created.get("id").asString());
    }

    private ResultActions app(String token, String method, String url, Object body) throws Exception {
        var builder = method.equals("GET") ? get(url) : post(url);
        builder.header("Authorization", "Bearer " + token);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        }
        return mvc.perform(builder);
    }

    private String askForCode(String token, UUID id) throws Exception {
        JsonNode sent = json(app(token, "POST", "/api/actions/" + id + "/approval-code", null).andExpect(status().isOk()));
        String code = sent.get("devCode").asString();
        assertThat(code).matches("\\d{6}");
        return code;
    }

    private static String notThis(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    @Test
    void aCodeApprovesTheRequestItWasSentFor() throws Exception {
        String phone = newPhone();
        String token = link(phone, "device-otp-1234");
        UUID id = createBill(phone);

        JsonNode sent = json(app(token, "POST", "/api/actions/" + id + "/approval-code", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resendAfterSeconds").value(30))
                .andExpect(jsonPath("$.expiresAt").exists()));
        String code = sent.get("devCode").asString();

        app(token, "POST", "/api/actions/" + id + "/approve", Map.of("method", "OTP", "code", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        app(token, "GET", "/api/actions/" + id, null).andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void aWrongCodeIsRefusedAndTheRequestKeepsWaiting() throws Exception {
        String phone = newPhone();
        String token = link(phone, "device-otp-2345");
        UUID id = createBill(phone);
        String code = askForCode(token, id);

        app(token, "POST", "/api/actions/" + id + "/approve", Map.of("method", "OTP", "code", notThis(code)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("not right")));
        app(token, "GET", "/api/actions/" + id, null).andExpect(jsonPath("$.status").value("CREATED"));

        // The right code still works, a wrong try did not use it up
        app(token, "POST", "/api/actions/" + id + "/approve", Map.of("method", "OTP", "code", code))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void fiveWrongTriesUseTheCodeUp() throws Exception {
        String phone = newPhone();
        String token = link(phone, "device-otp-3456");
        UUID id = createBill(phone);
        String code = askForCode(token, id);

        for (int i = 0; i < 5; i++) {
            app(token, "POST", "/api/actions/" + id + "/approve", Map.of("method", "OTP", "code", notThis(code)))
                    .andExpect(status().isBadRequest());
        }
        // Even the right code is refused now
        app(token, "POST", "/api/actions/" + id + "/approve", Map.of("method", "OTP", "code", code))
                .andExpect(status().isBadRequest());
        app(token, "GET", "/api/actions/" + id, null).andExpect(jsonPath("$.status").value("CREATED"));
        // and a second code cannot be asked for straight away
        app(token, "POST", "/api/actions/" + id + "/approval-code", null).andExpect(status().isTooManyRequests());
    }

    @Test
    void aCodeOnlyWorksForTheRequestItWasSentFor() throws Exception {
        String phone = newPhone();
        String token = link(phone, "device-otp-4567");
        UUID first = createBill(phone);
        UUID second = createBill(phone);
        String code = askForCode(token, first);

        app(token, "POST", "/api/actions/" + second + "/approve", Map.of("method", "OTP", "code", code))
                .andExpect(status().isBadRequest());
        app(token, "GET", "/api/actions/" + second, null).andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void aCodeThatWasUsedDoesNotWorkTwice() throws Exception {
        String phone = newPhone();
        String token = link(phone, "device-otp-5678");
        UUID id = createBill(phone);
        String code = askForCode(token, id);

        app(token, "POST", "/api/actions/" + id + "/approve", Map.of("method", "OTP", "code", code)).andExpect(status().isOk());
        // The request is approved now, so asking again is refused before any code check matters
        app(token, "POST", "/api/actions/" + id + "/approve", Map.of("method", "OTP", "code", code)).andExpect(status().isBadRequest());
    }

    @Test
    void onlyARequestThatIsWaitingGetsACode() throws Exception {
        String phone = newPhone();
        String token = link(phone, "device-otp-6789");
        UUID id = createBill(phone);
        app(token, "POST", "/api/actions/" + id + "/approve", Map.of("method", "BIOMETRIC")).andExpect(status().isOk());

        app(token, "POST", "/api/actions/" + id + "/approval-code", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("no longer waiting")));
    }

    @Test
    void otpWithoutAValidCodeShapeIsRefused() throws Exception {
        String phone = newPhone();
        String token = link(phone, "device-otp-7890");
        UUID id = createBill(phone);
        askForCode(token, id);

        app(token, "POST", "/api/actions/" + id + "/approve", Map.of("method", "OTP"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("6 digit")));
        app(token, "POST", "/api/actions/" + id + "/approve", Map.of("method", "OTP", "code", "12ab"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anotherCustomerCannotAskForACodeOrUseOne() throws Exception {
        String owner = newPhone();
        String stranger = newPhone();
        String ownerToken = link(owner, "device-otp-8901");
        String strangerToken = link(stranger, "device-otp-9012");
        UUID id = createBill(owner);
        String code = askForCode(ownerToken, id);

        app(strangerToken, "POST", "/api/actions/" + id + "/approval-code", null).andExpect(status().isNotFound());
        app(strangerToken, "POST", "/api/actions/" + id + "/approve", Map.of("method", "OTP", "code", code))
                .andExpect(status().isBadRequest());
        app(ownerToken, "GET", "/api/actions/" + id, null).andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void theApprovalCodeNeedsASignedInPhone() throws Exception {
        mvc.perform(post("/api/actions/" + UUID.randomUUID() + "/approval-code")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/business-operations/" + UUID.randomUUID() + "/approval-code")).andExpect(status().isUnauthorized());
    }

    @Autowired ApprovalCodeService service;

    @Test
    void aCodeForBusinessAndACodeForAPersonalRequestAreNotInterchangeable() throws Exception {
        String phone = newPhone();
        String token = link(phone, "device-otp-0123");
        UUID id = createBill(phone);
        String code = askForCode(token, id);

        // The same id used as a business request finds no code
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.check(phone, ApprovalCodeService.Kind.BUSINESS, id, code))
                .isInstanceOf(com.darajaagentx.api.exception.InvalidActionRequestException.class);
        // and it did not use the real one up
        service.check(phone, ApprovalCodeService.Kind.ACTION, id, code);
    }
}
