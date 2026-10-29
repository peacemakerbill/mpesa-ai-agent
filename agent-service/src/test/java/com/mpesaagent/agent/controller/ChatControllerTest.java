package com.mpesaagent.agent.controller;

import com.mpesaagent.agent.chat.ChatChannel;
import com.mpesaagent.agent.dto.InboundMessage;
import com.mpesaagent.agent.service.MessageProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerTest {

    private static final String KEY = "test-internal-key";

    @Autowired MockMvc mvc;
    @Autowired ChatChannel channel;
    @MockitoBean MessageProcessor processor;

    private static String phone() {
        return "2547" + String.format("%08d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 100_000_000L));
    }

    @Test
    void theChatEndpointsNeedTheInternalKey() throws Exception {
        mvc.perform(get("/internal/chat/254700000001/messages")).andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/chat/254700000001/send").contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"TEXT\",\"text\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aSentMessageIsStoredAndHandedToTheAssistant() throws Exception {
        String who = phone();

        mvc.perform(post("/internal/chat/" + who + "/send").header("X-Internal-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"clientMessageId\":\"c1\",\"kind\":\"TEXT\",\"text\":\"send 500 to Mama\"}"))
                .andExpect(status().isAccepted());

        verify(processor, timeout(3000)).process(any(InboundMessage.class));
        mvc.perform(get("/internal/chat/" + who + "/messages").header("X-Internal-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].dir").value("in"))
                .andExpect(jsonPath("$.messages[0].text").value("send 500 to Mama"))
                .andExpect(jsonPath("$.lastId").value(1));
    }

    @Test
    void sendingTheSameClientIdTwiceIsHandledOnce() throws Exception {
        String who = phone();
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/internal/chat/" + who + "/send").header("X-Internal-Key", KEY)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"clientMessageId\":\"same\",\"kind\":\"TEXT\",\"text\":\"hello\"}"))
                    .andExpect(status().isAccepted());
        }

        verify(processor, timeout(3000).times(1)).process(any(InboundMessage.class));
        mvc.perform(get("/internal/chat/" + who + "/messages").header("X-Internal-Key", KEY))
                .andExpect(jsonPath("$.messages.length()").value(1));
    }

    @Test
    void repliesFromTheAssistantAppearAfterTheNumberTheAppAlreadyHas() throws Exception {
        String who = phone();
        channel.sendText(who, "first");
        channel.sendText(who, "second");

        mvc.perform(get("/internal/chat/" + who + "/messages?after=1").header("X-Internal-Key", KEY))
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.messages[0].text").value("second"))
                .andExpect(jsonPath("$.typing").value(false));
    }

    @Test
    void badMessagesAreRefusedWithAReason() throws Exception {
        String who = phone();
        for (String body : new String[]{
                "{\"kind\":\"TEXT\",\"text\":\"  \"}",
                "{\"kind\":\"REACTION\",\"text\":\"x\"}",
                "{\"kind\":\"IMAGE\",\"mediaId\":\"nope\"}",
                "{\"kind\":\"LOCATION\"}",
                "{\"kind\":\"CONTACTS\"}",
                "{\"kind\":\"LIST_REPLY\"}"}) {
            mvc.perform(post("/internal/chat/" + who + "/send").header("X-Internal-Key", KEY)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }
        verify(processor, never()).process(any());
    }

    @Test
    void aFileIsUploadedFirstThenReferredToByItsId() throws Exception {
        String who = phone();
        MvcResult up = mvc.perform(post("/internal/chat/" + who + "/media").header("X-Internal-Key", KEY)
                        .contentType("image/png").header("X-File-Name", "pic.png").content(new byte[]{1, 2, 3, 4}))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mimeType").value("image/png"))
                .andExpect(jsonPath("$.size").value(4))
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(up.getResponse().getContentAsString(), "$.mediaId");

        mvc.perform(post("/internal/chat/" + who + "/send").header("X-Internal-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"IMAGE\",\"mediaId\":\"" + id + "\",\"caption\":\"my statement\"}"))
                .andExpect(status().isAccepted());

        verify(processor, timeout(3000)).process(any(InboundMessage.class));
        MvcResult down = mvc.perform(get("/internal/chat/" + who + "/media/" + id).header("X-Internal-Key", KEY))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png")).andReturn();
        assertThat(down.getResponse().getContentAsByteArray()).containsExactly(1, 2, 3, 4);
    }

    @Test
    void anotherCustomerCannotUseOrFetchAFile() throws Exception {
        String owner = phone();
        String other = phone();
        MvcResult up = mvc.perform(post("/internal/chat/" + owner + "/media").header("X-Internal-Key", KEY)
                .contentType("application/pdf").content(new byte[]{9})).andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(up.getResponse().getContentAsString(), "$.mediaId");

        mvc.perform(get("/internal/chat/" + other + "/media/" + id).header("X-Internal-Key", KEY)).andExpect(status().isNotFound());
        mvc.perform(post("/internal/chat/" + other + "/send").header("X-Internal-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"DOCUMENT\",\"mediaId\":\"" + id + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anEmptyUploadIsRefused() throws Exception {
        mvc.perform(post("/internal/chat/" + phone() + "/media").header("X-Internal-Key", KEY)
                        .contentType("image/png").content(new byte[0]))
                .andExpect(status().isBadRequest());
    }

    @Test
    void startingOverClearsTheChat() throws Exception {
        String who = phone();
        channel.sendText(who, "old");

        mvc.perform(delete("/internal/chat/" + who + "/messages").header("X-Internal-Key", KEY)).andExpect(status().isNoContent());

        mvc.perform(get("/internal/chat/" + who + "/messages").header("X-Internal-Key", KEY))
                .andExpect(jsonPath("$.messages.length()").value(0));
    }
}
