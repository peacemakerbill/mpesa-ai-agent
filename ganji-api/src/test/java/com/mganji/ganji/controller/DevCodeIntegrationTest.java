package com.mganji.ganji.controller;

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
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** With code-delivery set to dev (a test console), the code comes back in the answer and signs the phone in. */
@SpringBootTest(properties = "ganji.code-delivery=dev")
@AutoConfigureMockMvc
class DevCodeIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JsonMapper mapper;
    @MockitoBean RabbitTemplate rabbit;

    @Test
    void theCodeFromTheAnswerSignsThePhoneIn() throws Exception {
        String phone = "254711223344";
        DocumentContext issued = JsonPath.parse(mvc.perform(post("/api/auth/request-code").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("phone", phone))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String code = issued.read("$.devCode", String.class);
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