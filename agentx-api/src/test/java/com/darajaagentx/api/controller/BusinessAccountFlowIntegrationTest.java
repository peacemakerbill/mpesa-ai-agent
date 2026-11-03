package com.darajaagentx.api.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.darajaagentx.api.service.ChatRelay;
import com.darajaagentx.api.service.TokenProbe;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Business accounts through the real filters, controller, service and database (H2 with the real migrations). Safaricom is replaced. */
@SpringBootTest
@AutoConfigureMockMvc
class BusinessAccountFlowIntegrationTest {

    private static final String INTERNAL_KEY = "test-internal-key";
    private static final AtomicInteger NEXT = new AtomicInteger(40_000_000);
    private static final String KEY = "AbCdEfGhIjKlMnOpQrStUvWxYz123456";
    private static final String SECRET = "s3cr3tS3cr3tS3cr3t";
    private static final String HEADER = "label,environment,shortcode_type,shortcode,till_number,account_reference,parent_shortcode,consumer_key,consumer_secret,initiator_name,initiator_password,passkey";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean RabbitTemplate rabbit;
    @MockitoBean ChatRelay relay;
    @MockitoBean TokenProbe probe;

    private static String newPhone() {
        return "2547" + NEXT.incrementAndGet();
    }

    private String signIn(String phone) throws Exception {
        JsonNode code = mapper.readTree(mvc.perform(post("/internal/link-codes").header("X-Internal-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("phone", phone))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Map<String, Object> body = new HashMap<>();
        body.put("phone", phone);
        body.put("code", code.get("code").asString());
        body.put("deviceId", "device-" + phone);
        JsonNode token = mapper.readTree(mvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return "Bearer " + token.get("token").asString();
    }

    private static Map<String, Object> head(String shortcode) {
        Map<String, Object> m = new HashMap<>();
        m.put("label", "Main shop");
        m.put("environment", "sandbox");
        m.put("shortcodeType", "buy_goods");
        m.put("shortcode", shortcode);
        m.put("consumerKey", KEY);
        m.put("consumerSecret", SECRET);
        m.put("initiatorName", "testapi");
        m.put("initiatorPassword", "Safaricom999!*!");
        m.put("passkey", "bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919");
        return m;
    }

    @Test
    void everyBusinessCallNeedsASignedInPhone() throws Exception {
        mvc.perform(get("/api/business-accounts")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/business-accounts").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/business-accounts/csv-template")).andExpect(status().isUnauthorized());
    }

    @Test
    void anAccountIsSavedListedAndNeverShownWithItsSecrets() throws Exception {
        String auth = signIn(newPhone());
        String created = mvc.perform(post("/api/business-accounts").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(head("174379"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortcode").value("174379"))
                .andExpect(jsonPath("$.shortcodeType").value("BUY_GOODS"))
                .andExpect(jsonPath("$.consumerKeyHint").value("3456"))
                .andExpect(jsonPath("$.hasConsumerSecret").value(true))
                .andExpect(jsonPath("$.hasInitiatorPassword").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(created).doesNotContain(SECRET).doesNotContain("Safaricom999").doesNotContain("bfb279f9");

        String list = mvc.perform(get("/api/business-accounts").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain(SECRET).doesNotContain("consumerSecret\"");
    }

    @Test
    void aBranchEditAndDeleteWork() throws Exception {
        String auth = signIn(newPhone());
        JsonNode h = mapper.readTree(mvc.perform(post("/api/business-accounts").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(head("174379"))))
                .andReturn().getResponse().getContentAsString());
        String headId = h.get("id").asString();

        mvc.perform(post("/api/business-accounts").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Till 1\",\"tillNumber\":\"123456\",\"parentId\":\"" + headId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortcode").value("174379"))
                .andExpect(jsonPath("$.inheritsCredentials").value(true))
                .andExpect(jsonPath("$.hasConsumerSecret").value(true));

        mvc.perform(put("/api/business-accounts/" + headId).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Renamed\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.label").value("Renamed"))
                .andExpect(jsonPath("$.hasConsumerSecret").value(true));

        mvc.perform(delete("/api/business-accounts/" + headId).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.removed").value(2));
        mvc.perform(get("/api/business-accounts").header("Authorization", auth)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void mistakesComeBackAsPlainMessages() throws Exception {
        String auth = signIn(newPhone());
        Map<String, Object> bad = head("12");
        mvc.perform(post("/api/business-accounts").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The shortcode must be 5 to 7 digits."));
    }

    @Test
    void oneCustomerCannotSeeAnotherCustomersAccounts() throws Exception {
        String mine = signIn(newPhone());
        String theirs = signIn(newPhone());
        String id = mapper.readTree(mvc.perform(post("/api/business-accounts").header("Authorization", mine)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(head("174379"))))
                .andReturn().getResponse().getContentAsString()).get("id").asString();
        mvc.perform(get("/api/business-accounts").header("Authorization", theirs)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/business-accounts/" + id).header("Authorization", theirs)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/business-accounts/" + id).header("Authorization", theirs)).andExpect(status().isNotFound());
    }

    @Test
    void verifyReportsWhatSafaricomSaid() throws Exception {
        String auth = signIn(newPhone());
        String id = mapper.readTree(mvc.perform(post("/api/business-accounts").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(head("174379"))))
                .andReturn().getResponse().getContentAsString()).get("id").asString();
        when(probe.probe(any(), any(), any())).thenReturn(new TokenProbe.Outcome(TokenProbe.Kind.ACCEPTED, 200));
        mvc.perform(post("/api/business-accounts/" + id + "/verify").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true)).andExpect(jsonPath("$.verifiedAt").isNotEmpty());
        when(probe.probe(any(), any(), any())).thenReturn(new TokenProbe.Outcome(TokenProbe.Kind.REFUSED, 400));
        mvc.perform(post("/api/business-accounts/" + id + "/verify").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(false));
    }

    @Test
    void theTemplateDownloadsAsCsv() throws Exception {
        String auth = signIn(newPhone());
        mvc.perform(get("/api/business-accounts/csv-template").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("template.csv")))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("label,environment,shortcode_type")));
    }

    @Test
    void aCsvFileIsCheckedFirstAndThenSaved() throws Exception {
        String auth = signIn(newPhone());
        String file = HEADER + "\n"
                + "Main shop,sandbox,buy_goods,174379,,,," + KEY + "," + SECRET + ",,,\n"
                + "Till 1,sandbox,buy_goods,,123456,,174379,,,,,\n";
        MockMultipartFile csv = new MockMultipartFile("file", "accounts.csv", "text/csv", file.getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/business-accounts/import").file(csv).param("dryRun", "true").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.saved").value(false)).andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.issues.length()").value(0));
        mvc.perform(get("/api/business-accounts").header("Authorization", auth)).andExpect(jsonPath("$.length()").value(0));

        mvc.perform(multipart("/api/business-accounts/import").file(csv).param("dryRun", "false").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.saved").value(true));
        mvc.perform(get("/api/business-accounts").header("Authorization", auth)).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void aCsvFileWithAProblemSavesNothingAndNamesTheRow() throws Exception {
        String auth = signIn(newPhone());
        String file = HEADER + "\nBad,sandbox,paybill,12,,,," + KEY + "," + SECRET + ",,,\n";
        MockMultipartFile csv = new MockMultipartFile("file", "accounts.csv", "text/csv", file.getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/business-accounts/import").file(csv).param("dryRun", "false").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.saved").value(false))
                .andExpect(jsonPath("$.issues[0].row").value(2)).andExpect(jsonPath("$.issues[0].field").value("shortcode"));
        mvc.perform(get("/api/business-accounts").header("Authorization", auth)).andExpect(jsonPath("$.length()").value(0));
    }
}
