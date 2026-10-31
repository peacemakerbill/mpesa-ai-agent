package com.mganji.agent.client;

import com.mganji.agent.TestSupport;
import com.mganji.agent.exception.ModelException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ClaudeClientTest {

    private static final String OK = "{\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}";

    private MockRestServiceServer server;
    private ClaudeClient client;
    private final List<Long> pauses = new ArrayList<>();

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://claude.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ClaudeClient(builder.build(), pauses::add);
    }

    private ObjectNode body() {
        ObjectNode node = TestSupport.mapper().createObjectNode();
        node.put("model", "claude-sonnet-5-5");
        return node;
    }

    @Test
    void sendsTheRequestToTheMessagesEndpoint() {
        server.expect(requestTo("http://claude.test/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("claude-sonnet-5-5"))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        JsonNode response = client.createMessage(body());

        assertThat(response.path("content").get(0).path("text").asString()).isEqualTo("hi");
        server.verify();
    }

    @Test
    void retriesWhenTheServiceIsOverloadedThenSucceeds() {
        server.expect(requestTo("http://claude.test/v1/messages")).andRespond(withStatus(HttpStatusCode.valueOf(529)).body("{}"));
        server.expect(requestTo("http://claude.test/v1/messages")).andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS).body("{}"));
        server.expect(requestTo("http://claude.test/v1/messages")).andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        JsonNode response = client.createMessage(body());

        assertThat(response.has("content")).isTrue();
        assertThat(pauses).containsExactly(1000L, 2000L);
        server.verify();
    }

    @Test
    void givesUpAfterThreeAttempts() {
        for (int i = 0; i < 3; i++) {
            server.expect(requestTo("http://claude.test/v1/messages"))
                    .andRespond(withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE).body("down"));
        }

        assertThatThrownBy(() -> client.createMessage(body())).isInstanceOf(ModelException.class).hasMessageContaining("503");
        server.verify();
    }

    @Test
    void aBadRequestFailsAtOnceWithoutRetrying() {
        server.expect(requestTo("http://claude.test/v1/messages"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST).body("{\"error\":\"bad\"}"));

        assertThatThrownBy(() -> client.createMessage(body())).isInstanceOf(ModelException.class).hasMessageContaining("400");
        assertThat(pauses).isEmpty();
        server.verify();
    }

    @Test
    void aResponseWithoutContentIsAnError() {
        server.expect(requestTo("http://claude.test/v1/messages")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.createMessage(body())).isInstanceOf(ModelException.class);
    }
}