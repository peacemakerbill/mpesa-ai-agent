package com.mganji.ganji.controller;

import com.mganji.ganji.service.ChatRelay;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Signing in with a code and using the chat, with the real filters and database. The agent and RabbitMQ are replaced. */
@SpringBootTest
@AutoConfigureMockMvc
class ChatFlowIntegrationTest {

    private static final String KEY = "test-internal-key";
    private static final AtomicInteger NEXT = new AtomicInteger(30_000_000);

    @Autowired MockMvc mvc;
    @Autowired JsonMapper mapper;
    @MockitoBean RabbitTemplate rabbit;
    @MockitoBean ChatRelay relay;

    private static String newPhone() {
        return "2547" + NEXT.incrementAndGet();
    }

    /** Signs in the way a customer does when the code goes to their phone, using the internal code endpoint to read it. */
    private String signIn(String phone) throws Exception {
        DocumentContext code = JsonPath.parse(mvc.perform(post("/internal/link-codes").header("X-Internal-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("phone", phone))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Map<String, Object> body = new HashMap<>();
        body.put("phone", phone);
        body.put("code", code.read("$.code", String.class));
        body.put("deviceId", "device-" + phone);
        DocumentContext token = JsonPath.parse(mvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return token.read("$.token", String.class);
    }

    private static ResponseEntity<byte[]> ok(String json) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void askingForACodeNeedsNoTokenAndInLogModeDoesNotRevealIt() throws Exception {
        mvc.perform(post("/api/auth/request-code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + newPhone() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.devCode").doesNotExist());
    }

    @Test
    void aNumberThatIsNotAKenyanMobileIsRefused() throws Exception {
        mvc.perform(post("/api/auth/request-code").contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"12345\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theChatNeedsASignedInPhone() throws Exception {
        mvc.perform(get("/api/chat/messages")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/chat/messages").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/chat/media/00000000-0000-0000-0000-000000000000")).andExpect(status().isUnauthorized());
    }

    @Test
    void messagesAreSentAndReadForTheNumberInTheTokenNotTheRequest() throws Exception {
        String phone = newPhone();
        String token = signIn(phone);
        when(relay.call(eq(HttpMethod.POST), eq(phone), eq("/send"), isNull(), any(), any(), isNull()))
                .thenReturn(ResponseEntity.accepted().contentType(MediaType.APPLICATION_JSON).body("{\"messageId\":\"m1\"}".getBytes(StandardCharsets.UTF_8)));
        when(relay.call(eq(HttpMethod.GET), eq(phone), eq("/messages"), eq("after=4&limit=100"), isNull(), isNull(), isNull()))
                .thenReturn(ok("{\"messages\":[],\"typing\":true,\"lastId\":4}"));

        mvc.perform(post("/api/chat/messages").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"TEXT\",\"text\":\"hello\",\"from\":\"254799999999\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.messageId").value("m1"));
        mvc.perform(get("/api/chat/messages?after=4").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.typing").value(true));

        verify(relay, never()).call(any(), eq("254799999999"), any(), any(), any(), any(), any());
    }

    @Test
    void aFileIdThatIsNotAnIdIsNotForwarded() throws Exception {
        String token = signIn(newPhone());

        mvc.perform(get("/api/chat/media/..%2Fsecrets").header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());

        verify(relay, never()).call(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void anUploadIsPassedOnWithItsTypeAndName() throws Exception {
        String phone = newPhone();
        String token = signIn(phone);
        when(relay.call(eq(HttpMethod.POST), eq(phone), eq("/media"), isNull(), any(), eq(MediaType.IMAGE_PNG), eq("pic.png")))
                .thenReturn(ResponseEntity.status(201).contentType(MediaType.APPLICATION_JSON).body("{\"mediaId\":\"x\"}".getBytes(StandardCharsets.UTF_8)));

        mvc.perform(post("/api/chat/media").header("Authorization", "Bearer " + token).header("X-File-Name", "pic.png")
                        .contentType("image/png").content(new byte[]{1, 2, 3}))
                .andExpect(status().isCreated())
                .andExpect(content().string("{\"mediaId\":\"x\"}"));
    }
}