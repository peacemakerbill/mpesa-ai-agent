package com.darajaagentx.agent.chat.media;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Talks to a tiny S3 look-alike on this machine. It checks that every request is signed and that the signature is right. */
class S3BlobStoreTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-03T08:00:00Z"), ZoneOffset.UTC);
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private final List<String> problems = new CopyOnWriteArrayList<>();

    private HttpServer server() throws IOException {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        s.createContext("/", ex -> {
            String path = ex.getRequestURI().getRawPath();
            String method = ex.getRequestMethod();
            byte[] body = ex.getRequestBody().readAllBytes();
            String host = ex.getRequestHeaders().getFirst("Host");
            String hash = ex.getRequestHeaders().getFirst("x-amz-content-sha256");
            String date = ex.getRequestHeaders().getFirst("x-amz-date");
            Map<String, String> signed = new LinkedHashMap<>();
            signed.put("host", host);
            signed.put("x-amz-content-sha256", hash);
            signed.put("x-amz-date", date);
            String expected = new SigV4("AKIATEST", "secret", "eu-west-1").authorization(method, path, "", signed, hash, CLOCK.instant());
            if (!expected.equals(ex.getRequestHeaders().getFirst("Authorization"))) {
                problems.add("bad signature for " + method + " " + path);
                ex.sendResponseHeaders(403, -1);
                ex.close();
                return;
            }
            if (body.length > 0 && !SigV4.sha256Hex(body).equals(hash)) {
                problems.add("payload hash does not match the body");
            }
            switch (method) {
                case "PUT" -> { objects.put(path, body); ex.sendResponseHeaders(200, -1); }
                case "DELETE" -> { objects.remove(path); ex.sendResponseHeaders(204, -1); }
                case "GET" -> {
                    byte[] found = objects.get(path);
                    if (found == null) {
                        ex.sendResponseHeaders(404, -1);
                    } else {
                        ex.sendResponseHeaders(200, found.length);
                        ex.getResponseBody().write(found);
                    }
                }
                default -> ex.sendResponseHeaders(405, -1);
            }
            ex.close();
        });
        s.start();
        return s;
    }

    private S3BlobStore store(HttpServer s, String secret) {
        return new S3BlobStore("http://127.0.0.1:" + s.getAddress().getPort(), "chat-files", "eu-west-1", "AKIATEST", secret, true, "/chat/",
                Duration.ofDays(30), CLOCK, HttpClient.newHttpClient());
    }

    @Test
    void putGetDeleteWithValidSignatures() throws IOException {
        HttpServer s = server();
        try {
            S3BlobStore store = store(s, "secret");
            String key = UUID.randomUUID().toString();
            store.put(key, new byte[]{5, 6, 7}, Duration.ofDays(1));
            assertThat(objects.containsKey("/chat-files/chat/" + key)).isTrue();
            assertThat(Arrays.equals(store.get(key).get(), new byte[]{5, 6, 7})).isTrue();
            store.delete(key);
            assertThat(store.get(key).isPresent()).isEqualTo(false);
            assertThat(problems).isEmpty();
        } finally {
            s.stop(0);
        }
    }

    @Test
    void aWrongSecretIsRefusedAndNeverLooksLikeMissingFile() throws IOException {
        HttpServer s = server();
        try {
            S3BlobStore store = store(s, "wrong-secret");
            String key = UUID.randomUUID().toString();
            assertThatThrownBy(() -> store.put(key, new byte[]{1}, Duration.ofDays(1))).hasMessageContaining("did not accept");
            assertThatThrownBy(() -> store.get(key)).hasMessageContaining("not available");
        } finally {
            s.stop(0);
        }
    }

    @Test
    void refusesBadSettingsAndKeys() throws IOException {
        assertThatThrownBy(() -> new S3BlobStore("", "", "us-east-1", "a", "b", true, "", Duration.ZERO)).hasMessageContaining("needs bucket");
        assertThatThrownBy(() -> new S3BlobStore("", "Bad_Bucket", "us-east-1", "a", "b", true, "", Duration.ZERO)).hasMessageContaining("bucket name");
        assertThatThrownBy(() -> new S3BlobStore("", "ok-bucket", "us-east-1", "a", "b", true, "../x", Duration.ZERO)).hasMessageContaining("prefix");
        assertThatThrownBy(() -> new S3BlobStore("ftp://x", "ok-bucket", "us-east-1", "a", "b", true, "", Duration.ZERO)).hasMessageContaining("http(s)");
        HttpServer s = server();
        try {
            S3BlobStore store = store(s, "secret");
            assertThatThrownBy(() -> store.get("../../x")).isInstanceOf(IllegalArgumentException.class);
        } finally {
            s.stop(0);
        }
    }
}
