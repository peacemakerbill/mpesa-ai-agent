package com.mganji.ganji.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ChatRelayTest {

    private MockRestServiceServer server;
    private ChatRelay relay;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://agent.test");
        server = MockRestServiceServer.bindTo(builder).build();
        relay = new ChatRelay(builder.build());
    }

    private static String text(ResponseEntity<byte[]> r) {
        return new String(r.getBody(), StandardCharsets.UTF_8);
    }

    @Test
    void readingTheChatAsksForTheCustomersOwnNumberAndPassesTheAnswerOn() {
        server.expect(requestTo("http://agent.test/internal/chat/254712345678/messages?after=3&limit=50"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"messages\":[],\"typing\":false,\"lastId\":3}", MediaType.APPLICATION_JSON));

        ResponseEntity<byte[]> r = relay.call(HttpMethod.GET, "254712345678", "/messages", "after=3&limit=50", null, null, null);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(text(r)).contains("\"lastId\":3");
        server.verify();
    }

    @Test
    void anAnswerWithAnErrorKeepsItsStatusAndMessage() {
        server.expect(requestTo("http://agent.test/internal/chat/254712345678/send"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"The message is empty.\"}"));

        ResponseEntity<byte[]> r = relay.call(HttpMethod.POST, "254712345678", "/send", null, "{}".getBytes(StandardCharsets.UTF_8),
                MediaType.APPLICATION_JSON, null);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(text(r)).contains("The message is empty.");
    }

    @Test
    void anUploadKeepsItsTypeAndFileName() {
        server.expect(requestTo("http://agent.test/internal/chat/254712345678/media"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", "image/png"))
                .andExpect(header("X-File-Name", "pic.png"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("{\"mediaId\":\"x\"}"));

        ResponseEntity<byte[]> r = relay.call(HttpMethod.POST, "254712345678", "/media", null, new byte[]{1, 2},
                MediaType.IMAGE_PNG, "pic.png");

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void aFileKeepsItsTypeAndName() {
        server.expect(requestTo("http://agent.test/internal/chat/254712345678/media/abc"))
                .andRespond(withSuccess(new byte[]{9, 9}, MediaType.APPLICATION_PDF)
                        .header("Content-Disposition", "inline; filename=\"receipt.pdf\""));

        ResponseEntity<byte[]> r = relay.call(HttpMethod.GET, "254712345678", "/media/abc", null, null, null, null);

        assertThat(r.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(r.getHeaders().getFirst("Content-Disposition")).contains("receipt.pdf");
        assertThat(r.getBody()).containsExactly(9, 9);
    }

    @Test
    void anUnreachableAssistantIsABadGatewayWithAClearMessage() {
        server.expect(requestTo("http://agent.test/internal/chat/254712345678/messages"))
                .andRespond(request -> {
                    throw new IOException("connection refused");
                });

        ResponseEntity<byte[]> r = relay.call(HttpMethod.GET, "254712345678", "/messages", null, null, null, null);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(text(r)).contains("not reachable").doesNotContain("connection refused");
    }
}
