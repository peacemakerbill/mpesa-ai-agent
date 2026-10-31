package com.mganji.payment.controller;

import com.mganji.payment.dto.StkPushResponse;
import com.mganji.payment.event.PaymentCompletedEvent;
import com.mganji.payment.exception.MpesaApiException;
import com.mganji.payment.exception.MpesaCommunicationException;
import com.mganji.payment.service.MpesaClient;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end through the real controllers, services, filter, Flyway schema and database.
 * Only Daraja (MpesaClient) and RabbitMQ are replaced, so no network is needed.
 * Every test uses its own phone number, idempotency key and checkout id because the in memory
 * database is shared across the tests in this class.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentFlowIntegrationTest {

    private static final String INTERNAL_KEY = "test-internal-key";
    private static final String CALLBACK_URL = "/api/callbacks/stk/test-callback-secret";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean MpesaClient mpesa;
    @MockitoBean RabbitTemplate rabbitTemplate;

    private void stubPushAccepted(String checkoutId) {
        when(mpesa.stkPush(anyString(), anyInt(), anyString())).thenReturn(
                new StkPushResponse("mr-" + checkoutId, checkoutId, "0",
                        "Success. Request accepted for processing", "Success. Request accepted for processing"));
    }

    private ResultActions postPayment(String key, String phone, int amount, String reference) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "idempotencyKey", key, "phone", phone, "amount", amount, "accountReference", reference));
        return mvc.perform(post("/internal/payments")
                .header("X-Internal-Key", INTERNAL_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private JsonNode createPayment(String key, String phone, int amount, String reference) throws Exception {
        MvcResult result = postPayment(key, phone, amount, reference).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode fetch(String id) throws Exception {
        MvcResult result = mvc.perform(get("/internal/payments/" + id).header("X-Internal-Key", INTERNAL_KEY))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private ResultActions sendCallback(String url, String json) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String successCallback(String checkoutId, int amount, String receipt, String phone) {
        return """
                {"Body":{"stkCallback":{
                  "MerchantRequestID":"mr-%1$s","CheckoutRequestID":"%1$s",
                  "ResultCode":0,"ResultDesc":"The service request is processed successfully.",
                  "CallbackMetadata":{"Item":[
                    {"Name":"Amount","Value":%2$d.00},
                    {"Name":"MpesaReceiptNumber","Value":"%3$s"},
                    {"Name":"TransactionDate","Value":20261029191500},
                    {"Name":"PhoneNumber","Value":%4$s}]}}}}
                """.formatted(checkoutId, amount, receipt, phone);
    }

    private static String failureCallback(String checkoutId, String resultCode, String description) {
        return """
                {"Body":{"stkCallback":{
                  "MerchantRequestID":"mr-%1$s","CheckoutRequestID":"%1$s",
                  "ResultCode":%2$s,"ResultDesc":"%3$s"}}}
                """.formatted(checkoutId, resultCode, description);
    }

    @Test
    void createPaymentPushesStkAndRetryDoesNotPushTwice() throws Exception {
        stubPushAccepted("ws_CO_it1");

        JsonNode first = createPayment("it-key-1", "0712345601", 250, "ORD-1001");
        assertThat(first.get("status").asString()).isEqualTo("PUSHED");
        assertThat(first.get("phone").asString()).isEqualTo("254712345601");
        assertThat(first.get("checkoutRequestId").asString()).isEqualTo("ws_CO_it1");

        JsonNode second = createPayment("it-key-1", "0712345601", 250, "ORD-1001");
        assertThat(second.get("id").asString()).isEqualTo(first.get("id").asString());
        verify(mpesa, times(1)).stkPush("254712345601", 250, "ORD-1001");
    }

    @Test
    void successCallbackMarksPaymentSuccessPublishesOneEventAndIgnoresDuplicates() throws Exception {
        stubPushAccepted("ws_CO_it2");
        JsonNode payment = createPayment("it-key-2", "0712345602", 100, "ORD-1002");
        String callback = successCallback("ws_CO_it2", 100, "TST123ABC", "254712345602");

        sendCallback(CALLBACK_URL, callback)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResultCode").value(0));

        JsonNode after = fetch(payment.get("id").asString());
        assertThat(after.get("status").asString()).isEqualTo("SUCCESS");
        assertThat(after.get("mpesaReceipt").asString()).isEqualTo("TST123ABC");
        assertThat(after.get("resultCode").asString()).isEqualTo("0");

        sendCallback(CALLBACK_URL, callback).andExpect(status().isOk());

        verify(rabbitTemplate, timeout(2000).times(1))
                .convertAndSend(eq("payments.events"), eq("payment.done"), any(PaymentCompletedEvent.class));
    }

    @Test
    void cancelledCallbackWithTextResultCodeMarksPaymentCancelled() throws Exception {
        stubPushAccepted("ws_CO_it3");
        JsonNode payment = createPayment("it-key-3", "0712345603", 100, "ORD-1003");

        sendCallback(CALLBACK_URL, failureCallback("ws_CO_it3", "\"1032\"", "Request cancelled by user"))
                .andExpect(status().isOk());

        JsonNode after = fetch(payment.get("id").asString());
        assertThat(after.get("status").asString()).isEqualTo("CANCELLED");
        assertThat(after.get("mpesaReceipt")).isNull();
    }

    @Test
    void insufficientFundsCallbackMarksPaymentFailed() throws Exception {
        stubPushAccepted("ws_CO_it4");
        JsonNode payment = createPayment("it-key-4", "0712345604", 100, "ORD-1004");

        sendCallback(CALLBACK_URL, failureCallback("ws_CO_it4", "1", "The balance is insufficient for the transaction"))
                .andExpect(status().isOk());

        assertThat(fetch(payment.get("id").asString()).get("status").asString()).isEqualTo("FAILED");
    }

    @Test
    void callbackWithWrongSecretIsRejectedAndChangesNothing() throws Exception {
        stubPushAccepted("ws_CO_it5");
        JsonNode payment = createPayment("it-key-5", "0712345605", 100, "ORD-1005");

        sendCallback("/api/callbacks/stk/wrong-secret", successCallback("ws_CO_it5", 100, "FAKE000001", "254712345605"))
                .andExpect(status().isForbidden());

        assertThat(fetch(payment.get("id").asString()).get("status").asString()).isEqualTo("PUSHED");
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(PaymentCompletedEvent.class));
    }

    @Test
    void malformedCallbackIsABadRequest() throws Exception {
        sendCallback(CALLBACK_URL, "{}").andExpect(status().isBadRequest());
    }

    @Test
    void callbackForUnknownCheckoutIdIsAcknowledgedAndIgnored() throws Exception {
        sendCallback(CALLBACK_URL, successCallback("ws_CO_unknown", 100, "NOPE000001", "254712345699"))
                .andExpect(status().isOk());

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(PaymentCompletedEvent.class));
    }

    @Test
    void internalEndpointsRequireTheInternalKey() throws Exception {
        mvc.perform(post("/internal/payments").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/payments/" + UUID.randomUUID()).header("X-Internal-Key", "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidPhoneIsABadRequest() throws Exception {
        postPayment("it-key-6", "12345", 100, "ORD-1006").andExpect(status().isBadRequest());
    }

    @Test
    void overlongAccountReferenceIsABadRequest() throws Exception {
        postPayment("it-key-7", "0712345607", 100, "THIS-REFERENCE-IS-TOO-LONG")
                .andExpect(status().isBadRequest());
    }

    @Test
    void amountAboveTheTransactionLimitIsRejected() throws Exception {
        postPayment("it-key-8", "0712345608", 70001, "ORD-1008").andExpect(status().isUnprocessableContent());
    }

    @Test
    void reusingAKeyForADifferentAmountIsAConflict() throws Exception {
        stubPushAccepted("ws_CO_it9");
        createPayment("it-key-9", "0712345609", 100, "ORD-1009");

        postPayment("it-key-9", "0712345609", 200, "ORD-1009").andExpect(status().isConflict());
    }

    @Test
    void darajaRejectionMarksPaymentFailedWithTheReason() throws Exception {
        when(mpesa.stkPush(anyString(), anyInt(), anyString()))
                .thenThrow(new MpesaApiException("400.002.02", "Bad Request - Invalid PhoneNumber"));

        JsonNode payment = createPayment("it-key-10", "0712345610", 100, "ORD-1010");

        assertThat(payment.get("status").asString()).isEqualTo("FAILED");
        assertThat(payment.get("resultDesc").asString()).contains("Invalid PhoneNumber");
    }

    @Test
    void networkFailureKeepsPaymentPendingAndRetryDoesNotPushAgain() throws Exception {
        when(mpesa.stkPush(anyString(), anyInt(), anyString()))
                .thenThrow(new MpesaCommunicationException("timeout", null));

        JsonNode first = createPayment("it-key-11", "0712345611", 100, "ORD-1011");
        assertThat(first.get("status").asString()).isEqualTo("PENDING");

        JsonNode second = createPayment("it-key-11", "0712345611", 100, "ORD-1011");
        assertThat(second.get("id").asString()).isEqualTo(first.get("id").asString());
        verify(mpesa, times(1)).stkPush(anyString(), anyInt(), anyString());
    }

    @Test
    void latestEndpointReturnsTheNewestPaymentForAPhone() throws Exception {
        when(mpesa.stkPush(anyString(), anyInt(), anyString())).thenReturn(
                new StkPushResponse("mr-a", "ws_CO_it12a", "0", "ok", "ok"),
                new StkPushResponse("mr-b", "ws_CO_it12b", "0", "ok", "ok"));

        createPayment("it-key-12a", "0712345612", 100, "ORD-1012");
        Thread.sleep(10);
        JsonNode newest = createPayment("it-key-12b", "0712345612", 150, "ORD-1013");

        mvc.perform(get("/internal/payments/latest").param("phone", "0712345612").header("X-Internal-Key", INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(newest.get("id").asString()))
                .andExpect(jsonPath("$.amount").value(150));
    }

    @Test
    void unknownPaymentIsNotFound() throws Exception {
        mvc.perform(get("/internal/payments/" + UUID.randomUUID()).header("X-Internal-Key", INTERNAL_KEY))
                .andExpect(status().isNotFound());
    }
}