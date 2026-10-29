package com.mpesaagent.agent.controller;

import com.mpesaagent.agent.dto.InboundMessage;
import com.mpesaagent.agent.service.MessageProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InboundControllerTest {

    private static final String KEY = "test-internal-key";

    @Autowired MockMvc mvc;
    @MockitoBean MessageProcessor processor;

    private static String body(String id, String from) {
        return "{\"messageId\":\"" + id + "\",\"from\":\"" + from + "\",\"timestamp\":1,\"kind\":\"TEXT\",\"text\":\"hello\"}";
    }

    @Test
    void callsWithoutTheInternalKeyAreRejected() throws Exception {
        mvc.perform(post("/internal/inbound").contentType(MediaType.APPLICATION_JSON).content(body("wamid.nokey", "254700000001")))
                .andExpect(status().isUnauthorized());

        verify(processor, never()).process(any());
    }

    @Test
    void callsWithAWrongKeyAreRejected() throws Exception {
        mvc.perform(post("/internal/inbound").header("X-Internal-Key", "nope")
                        .contentType(MediaType.APPLICATION_JSON).content(body("wamid.wrongkey", "254700000001")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aValidMessageIsAcceptedAndHandledInTheBackground() throws Exception {
        String id = "wamid." + UUID.randomUUID();

        mvc.perform(post("/internal/inbound").header("X-Internal-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body(id, "254700000001")))
                .andExpect(status().isAccepted());

        verify(processor, timeout(3000)).process(any(InboundMessage.class));
    }

    @Test
    void theSameMessageDeliveredTwiceIsHandledOnce() throws Exception {
        String id = "wamid." + UUID.randomUUID();

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/internal/inbound").header("X-Internal-Key", KEY)
                            .contentType(MediaType.APPLICATION_JSON).content(body(id, "254700000009")))
                    .andExpect(status().isAccepted());
        }

        verify(processor, timeout(3000).times(1)).process(any(InboundMessage.class));
    }

    @Test
    void aMessageWithoutAnIdOrSenderIsABadRequest() throws Exception {
        mvc.perform(post("/internal/inbound").header("X-Internal-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"TEXT\",\"text\":\"hello\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/internal/inbound").header("X-Internal-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageId\":\"wamid.x\",\"timestamp\":1,\"text\":\"hello\"}"))
                .andExpect(status().isBadRequest());
    }
}
