package com.mganji.agent.client;

import com.mganji.agent.TestSupport;
import com.mganji.agent.dto.WalletActionView;
import com.mganji.agent.exception.GanjiApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GanjiClientTest {

    private static final UUID ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final String ACTION = "{\"id\":\"" + ID + "\",\"type\":\"PAY_TILL\",\"status\":\"CREATED\",\"amount\":300,"
            + "\"summary\":\"Pay KES 300 to Till 123456\",\"warnings\":[\"Never share your PIN\"],\"needsContact\":false,\"unknownField\":1}";

    private MockRestServiceServer server;
    private GanjiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ganji.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GanjiClient(builder.build(), TestSupport.mapper());
    }

    @Test
    void createSendsTheBodyAndIgnoresUnknownFields() {
        server.expect(requestTo("http://ganji.test/internal/actions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.phone").value("254700000001"))
                .andExpect(jsonPath("$.details.till").value("123456"))
                .andRespond(withSuccess(ACTION, MediaType.APPLICATION_JSON));

        WalletActionView view = client.createAction(Map.of("phone", "254700000001", "details", Map.of("till", "123456")));

        assertThat(view.id()).isEqualTo(ID);
        assertThat(view.summary()).isEqualTo("Pay KES 300 to Till 123456");
        server.verify();
    }

    @Test
    void listReturnsTheRequests() {
        server.expect(requestTo("http://ganji.test/internal/actions?phone=254700000001&limit=5"))
                .andRespond(withSuccess("[" + ACTION + "]", MediaType.APPLICATION_JSON));

        List<WalletActionView> list = client.list("254700000001", 5);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).status()).isEqualTo("CREATED");
    }

    @Test
    void cancelAndReceiptPostToTheRightPaths() {
        server.expect(requestTo("http://ganji.test/internal/actions/" + ID + "/cancel"))
                .andExpect(jsonPath("$.phone").value("254700000001"))
                .andRespond(withSuccess(ACTION, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://ganji.test/internal/actions/" + ID + "/receipt"))
                .andExpect(jsonPath("$.receipt").value("SGL1234567"))
                .andRespond(withSuccess(ACTION, MediaType.APPLICATION_JSON));

        client.cancel(ID, "254700000001");
        client.attachReceipt(ID, "254700000001", "SGL1234567");

        server.verify();
    }

    @Test
    void errorsKeepTheStatusAndTheServersMessage() {
        server.expect(requestTo("http://ganji.test/internal/actions"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":400,\"message\":\"The paybill number must be 5 to 7 digits\"}"));

        assertThatThrownBy(() -> client.createAction(Map.of()))
                .isInstanceOfSatisfying(GanjiApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(400);
                    assertThat(e.getMessage()).contains("5 to 7 digits");
                });
    }

    @Test
    void aServerErrorIsTreatedAsUnavailable() {
        server.expect(requestTo("http://ganji.test/internal/actions/" + ID + "/cancel"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("<html>bad gateway</html>"));

        assertThatThrownBy(() -> client.cancel(ID, "254700000001"))
                .isInstanceOfSatisfying(GanjiApiException.class, e -> {
                    assertThat(e.isUnavailable()).isTrue();
                    assertThat(e.getMessage()).doesNotContain("html");
                });
    }
}
