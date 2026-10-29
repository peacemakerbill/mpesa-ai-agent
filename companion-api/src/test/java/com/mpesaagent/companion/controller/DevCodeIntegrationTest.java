package com.mpesaagent.companion.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** With code-delivery set to dev (a test console), the code comes back in the answer and signs the phone in. */
@SpringBootTest(properties = "companion.code-delivery=dev")
@AutoConfigureMockMvc
class DevCodeIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean RabbitTemplate rabbit;

    @Test
    void theCodeFromTheAnswerSignsThePhoneIn() throws Exception {
        String phone = "254711223344";
        JsonNode issued = mapper.readTree(mvc.perform(post("/api/auth/request-code").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("phone", phone))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String code = issued.get("devCode").asText();
        assertThat(code).matches("\\d{6}");

        mvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("phone", phone, "code", code, "deviceId", "console-device-1"))))
                .andExpect(status().isOk());

        // A used code does not work twice
        mvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("phone", phone, "code", code, "deviceId", "console-device-1"))))
                .andExpect(status().isUnauthorized());
    }
}
