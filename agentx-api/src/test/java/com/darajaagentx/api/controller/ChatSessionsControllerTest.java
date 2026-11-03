package com.darajaagentx.api.controller;

import com.darajaagentx.api.config.DevicePrincipal;
import com.darajaagentx.api.exception.TooManyRequestsException;
import com.darajaagentx.api.service.ChatRelay;
import com.darajaagentx.api.service.SlidingWindowLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The relay is replaced by one that remembers what it was asked, so no agent-service is needed. */
class ChatSessionsControllerTest {

    private record Call(HttpMethod method, String phone, String path, String body) {
    }

    private final List<Call> calls = new ArrayList<>();
    private final ChatRelay relay = new ChatRelay(null) {
        @Override
        public ResponseEntity<byte[]> call(HttpMethod method, String phone, String path, String query, byte[] body,
                                           MediaType contentType, String fileName) {
            calls.add(new Call(method, phone, path, body == null ? null : new String(body, StandardCharsets.UTF_8)));
            return ResponseEntity.ok().body("{}".getBytes(StandardCharsets.UTF_8));
        }
    };
    private final ChatSessionsController controller = new ChatSessionsController(relay, new SlidingWindowLimiter(Clock.systemUTC()));
    private final DevicePrincipal who = new DevicePrincipal("254700000001", "device-1");

    @Test
    void everyCallUsesTheNumberFromTheToken() {
        controller.list(who);
        controller.create(who);
        controller.open("abc123", who);
        controller.rename("abc123", "{\"title\":\"Rent\"}".getBytes(StandardCharsets.UTF_8), who);
        controller.pin("abc123", "{\"pinned\":true}".getBytes(StandardCharsets.UTF_8), who);
        controller.delete("abc123", who);
        controller.deleteAll(who);

        assertThat(calls).hasSize(7);
        assertThat(calls.stream().allMatch(c -> c.phone().equals("254700000001"))).isTrue();
        assertThat(calls.get(0)).isEqualTo(new Call(HttpMethod.GET, "254700000001", "/sessions", null));
        assertThat(calls.get(1)).isEqualTo(new Call(HttpMethod.POST, "254700000001", "/sessions", null));
        assertThat(calls.get(2)).isEqualTo(new Call(HttpMethod.POST, "254700000001", "/sessions/abc123/open", null));
        assertThat(calls.get(3)).isEqualTo(new Call(HttpMethod.PUT, "254700000001", "/sessions/abc123/title", "{\"title\":\"Rent\"}"));
        assertThat(calls.get(4)).isEqualTo(new Call(HttpMethod.PUT, "254700000001", "/sessions/abc123/pin", "{\"pinned\":true}"));
        assertThat(calls.get(5)).isEqualTo(new Call(HttpMethod.DELETE, "254700000001", "/sessions/abc123", null));
        assertThat(calls.get(6)).isEqualTo(new Call(HttpMethod.DELETE, "254700000001", "/sessions", null));
    }

    @Test
    void anIdThatCouldNotBeOneOfOursIsAnsweredWithoutAskingTheAgent() {
        assertThat(controller.open("../x", who).getStatusCode().value()).isEqualTo(404);
        assertThat(controller.delete("a b", who).getStatusCode().value()).isEqualTo(404);
        assertThat(controller.rename("a{b}", "{}".getBytes(StandardCharsets.UTF_8), who).getStatusCode().value()).isEqualTo(404);
        assertThat(controller.pin("x".repeat(41), "{}".getBytes(StandardCharsets.UTF_8), who).getStatusCode().value()).isEqualTo(404);

        assertThat(calls).isEmpty();
    }

    @Test
    void aHugeBodyIsRefusedBeforeItIsPassedOn() {
        byte[] big = ("{\"title\":\"" + "x".repeat(3000) + "\"}").getBytes(StandardCharsets.UTF_8);

        assertThat(controller.rename("abc123", big, who).getStatusCode().value()).isEqualTo(400);
        assertThat(calls).isEmpty();
    }

    @Test
    void changingChatsTooQuicklyIsSlowedDown() {
        for (int i = 0; i < 60; i++) {
            controller.create(who);
        }

        assertThatThrownBy(() -> controller.create(who)).isInstanceOf(TooManyRequestsException.class);
        // Reading the list is not limited
        controller.list(who);
    }
}