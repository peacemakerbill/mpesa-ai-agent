package com.mpesaagent.payment.controller;

import com.mpesaagent.payment.dto.DarajaAsyncResponse;
import com.mpesaagent.payment.model.MpesaOperation;
import com.mpesaagent.payment.model.Payment;
import com.mpesaagent.payment.model.PaymentStatus;
import com.mpesaagent.payment.repository.C2bTransactionRepository;
import com.mpesaagent.payment.repository.MpesaOperationRepository;
import com.mpesaagent.payment.repository.PaymentRepository;
import com.mpesaagent.payment.service.MpesaOperationsClient;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Refund, maker checker approval, Daraja result callback, receipts and direct Paybill payments, end to end
 * with the real controllers, services and database. Only Daraja and RabbitMQ are replaced.
 * Staff phones come from src/test/resources/application.yml: 0711000001 STAFF, 0711000002 APPROVER, 0711000003 ADMIN.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OperationFlowIntegrationTest {

    private static final String KEY = "test-internal-key";
    private static final String RESULT_URL = "/api/callbacks/ops/result/test-callback-secret";
    private static final String C2B_URL = "/api/callbacks/c2b/confirmation/test-callback-secret";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PaymentRepository payments;
    @Autowired MpesaOperationRepository operations;
    @Autowired C2bTransactionRepository c2b;

    @MockitoBean MpesaOperationsClient daraja;
    @MockitoBean RabbitTemplate rabbitTemplate;

    private Payment successfulPayment(String phone, int amount) {
        Payment p = new Payment("pay-" + UUID.randomUUID(), phone, amount, "ORD-1");
        p.setStatus(PaymentStatus.SUCCESS);
        p.setMpesaReceipt("RCPT" + Math.abs(UUID.randomUUID().hashCode() % 100000));
        return payments.saveAndFlush(p);
    }

    private ResultActions createOperation(Map<String, Object> body) throws Exception {
        return mvc.perform(post("/internal/operations").header("X-Internal-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
    }

    private ResultActions act(String path, String actor) throws Exception {
        return mvc.perform(post("/internal/operations/" + path).header("X-Internal-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("actorPhone", actor))));
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return mapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    @Test
    void customerRefundIsApprovedBySomeoneElseSentToMpesaAndCompletedByCallback() throws Exception {
        Payment payment = successfulPayment("254700000010", 300);
        when(daraja.b2c(anyString(), anyInt(), anyString(), anyString(), anyString()))
                .thenReturn(new DarajaAsyncResponse("AG_it1", "ignored", "0", "Accept the service request successfully."));

        JsonNode created = json(createOperation(Map.of(
                "idempotencyKey", "op-it-1", "type", "REFUND", "actorPhone", "0700000010",
                "relatedPaymentId", payment.getId().toString(), "reason", "changed my mind"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL")));
        String id = created.get("id").asString();
        assertThat(created.get("amount").asInt()).isEqualTo(300);
        assertThat(created.get("recipientPhone").asString()).isEqualTo("254700000010");
        verify(daraja, never()).b2c(anyString(), anyInt(), anyString(), anyString(), anyString());

        // the customer, a plain staff member and an unrelated person cannot approve
        act(id + "/approve", "0700000010").andExpect(status().isForbidden());
        act(id + "/approve", "0711000001").andExpect(status().isForbidden());

        // an approver can
        act(id + "/approve", "0711000002")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.approvedBy").value("254711000002"));
        verify(daraja, times(1)).b2c(anyString(), eq(300), eq("254700000010"), anyString(), anyString());

        // approving twice does not pay twice
        act(id + "/approve", "0711000002").andExpect(status().isConflict());
        verify(daraja, times(1)).b2c(anyString(), anyInt(), anyString(), anyString(), anyString());

        // Safaricom reports success using the id we generated
        MpesaOperation stored = operations.findById(UUID.fromString(id)).orElseThrow();
        String result = """
                {"Result":{"ResultType":0,"ResultCode":0,"ResultDesc":"The service request is processed successfully.",
                  "OriginatorConversationID":"%s","ConversationID":"AG_it1","TransactionID":"QIT1ABC123",
                  "ResultParameters":{"ResultParameter":[{"Key":"TransactionReceipt","Value":"QIT1ABC123"}]}}}
                """.formatted(stored.getOriginatorConversationId());
        mvc.perform(post(RESULT_URL).contentType(MediaType.APPLICATION_JSON).content(result))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ResultCode").value(0));
        // the same result delivered twice is harmless
        mvc.perform(post(RESULT_URL).contentType(MediaType.APPLICATION_JSON).content(result)).andExpect(status().isOk());

        mvc.perform(get("/internal/operations/" + id).param("actorPhone", "0700000010").header("X-Internal-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.mpesaReceipt").value("QIT1ABC123"));

        // and a second refund of the same payment cannot exceed what is left (nothing)
        createOperation(Map.of("idempotencyKey", "op-it-1b", "type", "REFUND", "actorPhone", "0700000010",
                "relatedPaymentId", payment.getId().toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customerCannotRefundSomeoneElsesPaymentOrUseStaffOperations() throws Exception {
        Payment payment = successfulPayment("254700000011", 100);

        createOperation(Map.of("idempotencyKey", "op-it-2", "type", "REFUND", "actorPhone", "0700000099",
                "relatedPaymentId", payment.getId().toString())).andExpect(status().isForbidden());
        createOperation(Map.of("idempotencyKey", "op-it-3", "type", "DISBURSEMENT", "actorPhone", "0700000099",
                "amount", 100, "recipientPhone", "0700000099")).andExpect(status().isForbidden());
        createOperation(Map.of("idempotencyKey", "op-it-4", "type", "ACCOUNT_BALANCE", "actorPhone", "0700000099"))
                .andExpect(status().isForbidden());
    }

    @Test
    void nobodyCanApproveTheirOwnPayout() throws Exception {
        JsonNode created = json(createOperation(Map.of(
                "idempotencyKey", "op-it-5", "type", "DISBURSEMENT", "actorPhone", "0711000003",
                "amount", 500, "recipientPhone", "0722000005"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_APPROVAL")));

        act(created.get("id").asString() + "/approve", "0711000003").andExpect(status().isForbidden());

        // a different approver can still decline it
        act(created.get("id").asString() + "/reject", "0711000002")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void callbackWithWrongSecretIsRefused() throws Exception {
        mvc.perform(post("/api/callbacks/ops/result/wrong-secret").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"Result\":{\"ResultCode\":0,\"OriginatorConversationID\":\"x\"}}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void directPaybillPaymentIsRecordedOnce() throws Exception {
        String body = """
                {"TransactionType":"Pay Bill","TransID":"C2BIT0001","TransTime":"20261029191500","TransAmount":"150.00",
                 "BusinessShortCode":"174379","BillRefNumber":"254700000012","OrgAccountBalance":"1000.00",
                 "MSISDN":"hash","FirstName":"Jane","LastName":"Doe"}
                """;
        long before = c2b.count();

        mvc.perform(post(C2B_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ResultCode").value("0"));
        mvc.perform(post(C2B_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        assertThat(c2b.count()).isEqualTo(before + 1);
    }

    @Test
    void receiptIsAPdfForTheOwnerAndNotFoundForAnyoneElse() throws Exception {
        Payment payment = successfulPayment("254700000013", 800);

        byte[] pdf = mvc.perform(get("/internal/payments/" + payment.getId() + "/receipt")
                        .param("phone", "0700000013").header("X-Internal-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");

        mvc.perform(get("/internal/payments/" + payment.getId() + "/receipt")
                        .param("phone", "0700000099").header("X-Internal-Key", KEY))
                .andExpect(status().isNotFound());
        mvc.perform(get("/internal/payments/" + payment.getId())
                        .param("phone", "0700000099").header("X-Internal-Key", KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    void staffLookupReportsRoleAndTreatsStrangersAsCustomers() throws Exception {
        mvc.perform(get("/internal/staff/0711000002").header("X-Internal-Key", KEY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("APPROVER"));
        mvc.perform(get("/internal/staff/0700000777").header("X-Internal-Key", KEY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("CUSTOMER"));
    }
}