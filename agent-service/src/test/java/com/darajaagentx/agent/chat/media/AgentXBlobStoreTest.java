package com.darajaagentx.agent.chat.media;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Talks to a tiny look-alike of the internal files address of agentx-api. */
class AgentXBlobStoreTest {

    private static final String ID = "6f1c2b9e-0000-4000-8000-000000000001";
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private final List<String> seen = new CopyOnWriteArrayList<>();
    private volatile int forcedStatus = 0;
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private AgentXBlobStore store() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/files/chat-media/", ex -> {
            String name = ex.getRequestURI().getPath().substring("/internal/files/chat-media/".length());
            seen.add(ex.getRequestMethod() + " " + name + " " + ex.getRequestURI().getQuery() + " key=" + ex.getRequestHeaders().getFirst("X-Internal-Key"));
            byte[] body = ex.getRequestBody().readAllBytes();
            if (forcedStatus != 0) {
                ex.sendResponseHeaders(forcedStatus, -1);
                ex.close();
                return;
            }
            switch (ex.getRequestMethod()) {
                case "PUT" -> {
                    files.put(name, body);
                    ex.sendResponseHeaders(204, -1);
                }
                case "GET" -> {
                    byte[] found = files.get(name);
                    if (found == null) {
                        ex.sendResponseHeaders(404, -1);
                    } else {
                        ex.sendResponseHeaders(200, found.length == 0 ? -1 : found.length);
                        ex.getResponseBody().write(found);
                    }
                }
                default -> {
                    files.remove(name);
                    ex.sendResponseHeaders(204, -1);
                }
            }
            ex.close();
        });
        server.start();
        RestClient http = RestClient.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .defaultHeader("X-Internal-Key", "k").build();
        return new AgentXBlobStore(http, Duration.ofDays(30));
    }

    @Test
    void savesReadsAndRemovesAFile() throws Exception {
        AgentXBlobStore store = store();
        byte[] bytes = {0, 1, (byte) 255, 10};
        store.put(ID, bytes, Duration.ofDays(1));
        assertThat(files.get(ID)).containsExactly(bytes);
        assertThat(seen.get(0)).isEqualTo("PUT " + ID + " keepSeconds=86400 key=k");
        assertThat(store.get(ID).orElseThrow()).containsExactly(bytes);
        store.delete(ID);
        assertThat(store.get(ID)).isEmpty();
        assertThat(store.kind()).isEqualTo("agentx");
        assertThat(store.defaultRetention()).isEqualTo(Duration.ofDays(30));
    }

    @Test
    void theMetaFileUsesTheSameAddress() throws Exception {
        AgentXBlobStore store = store();
        store.put(ID + ".meta", new byte[]{1}, Duration.ofHours(1));
        assertThat(files).containsKey(ID + ".meta");
    }

    @Test
    void aFullOrRefusingStoreGivesAClearError() throws Exception {
        AgentXBlobStore store = store();
        forcedStatus = 413;
        assertThatThrownBy(() -> store.put(ID, new byte[]{1}, Duration.ofDays(1))).isInstanceOf(IllegalStateException.class).hasMessageContaining("too large");
        forcedStatus = 503;
        assertThatThrownBy(() -> store.put(ID, new byte[]{1}, Duration.ofDays(1))).hasMessageContaining("503");
        assertThatThrownBy(() -> store.get(ID)).hasMessageContaining("503");
        store.delete(ID);   // a failed delete is only logged
    }

    @Test
    void anUnreachableServerIsReportedAndNothingIsLeaked() throws Exception {
        AgentXBlobStore store = new AgentXBlobStore(RestClient.create("http://127.0.0.1:1"), Duration.ofDays(1));
        assertThatThrownBy(() -> store.put(ID, new byte[]{1}, Duration.ofDays(1))).hasMessageContaining("not reachable");
        assertThatThrownBy(() -> store.get(ID)).hasMessageContaining("not reachable");
        store.delete(ID);
    }

    @Test
    void onlyOurOwnKeysAreSent() throws Exception {
        AgentXBlobStore store = store();
        assertThatThrownBy(() -> store.put("../x", new byte[]{1}, Duration.ofDays(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.get("a/b")).isInstanceOf(IllegalArgumentException.class);
        assertThat(seen).isEmpty();
    }
}
