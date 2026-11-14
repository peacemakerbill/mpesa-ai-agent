package com.darajaagentx.api.storage;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Talks to a tiny look-alike of Cloudinary on this machine. It checks the way the requests are signed against the
 * documented method, which proves the code follows that method. It does not prove Cloudinary accepts it: try the real
 * account once (see the storage notes).
 */
class CloudinaryFileStoreTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-14T10:00:00Z"), ZoneOffset.UTC);
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private final List<String> problems = new CopyOnWriteArrayList<>();
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static String sha1Hex(String s) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8)));
    }

    /** Reads a multipart body as text (ISO 8859-1 keeps every byte) into its fields, and the bytes of the file part. */
    private static Map<String, String> fields(String contentType, byte[] body, byte[][] fileOut) {
        String boundary = "--" + contentType.substring(contentType.indexOf("boundary=") + 9);
        String text = new String(body, StandardCharsets.ISO_8859_1);
        Map<String, String> out = new TreeMap<>();
        for (String part : text.split(java.util.regex.Pattern.quote(boundary))) {
            int nameAt = part.indexOf("name=\"");
            if (nameAt < 0) {
                continue;
            }
            String name = part.substring(nameAt + 6, part.indexOf('"', nameAt + 6));
            int dataAt = part.indexOf("\r\n\r\n") + 4;
            String value = part.substring(dataAt, part.length() - 2);
            if (name.equals("file")) {
                fileOut[0] = value.getBytes(StandardCharsets.ISO_8859_1);
            } else {
                out.put(name, value);
            }
        }
        return out;
    }

    private CloudinaryFileStore store(String secret) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            try {
                String path = ex.getRequestURI().getRawPath();
                byte[] body = ex.getRequestBody().readAllBytes();
                int status = 200;
                byte[] answer = "{}".getBytes(StandardCharsets.UTF_8);
                if (path.equals("/v1_1/democloud/raw/upload") || path.equals("/v1_1/democloud/raw/destroy")) {
                    byte[][] file = new byte[1][];
                    Map<String, String> f = fields(ex.getRequestHeaders().getFirst("Content-Type"), body, file);
                    Map<String, String> signed = new TreeMap<>(f);
                    String given = signed.remove("signature");
                    String key = signed.remove("api_key");
                    StringBuilder sb = new StringBuilder();
                    for (Map.Entry<String, String> e : signed.entrySet()) {
                        sb.append(sb.length() == 0 ? "" : "&").append(e.getKey()).append('=').append(e.getValue());
                    }
                    if (!"12345".equals(key) || !sha1Hex(sb + "s3cret").equals(given)) {
                        problems.add("bad signature on " + path);
                        status = 401;
                    } else if (!"authenticated".equals(f.get("type"))) {
                        problems.add("not authenticated type");
                        status = 400;
                    } else if (path.endsWith("/upload")) {
                        objects.put(f.get("public_id"), file[0]);
                    } else {
                        objects.remove(f.get("public_id"));
                    }
                } else if (path.startsWith("/democloud/raw/authenticated/s--")) {
                    String rest = path.substring("/democloud/raw/authenticated/s--".length());
                    String sig = rest.substring(0, 8);
                    String publicId = rest.substring(rest.indexOf("--/") + 3);
                    String expected = Base64.getUrlEncoder().withoutPadding()
                            .encodeToString(MessageDigest.getInstance("SHA-1").digest((publicId + "s3cret").getBytes(StandardCharsets.UTF_8))).substring(0, 8);
                    if (!expected.equals(sig)) {
                        problems.add("bad delivery signature");
                        status = 401;
                    } else if (!objects.containsKey(publicId)) {
                        status = 404;
                    } else {
                        answer = objects.get(publicId);
                    }
                } else {
                    status = 404;
                }
                ex.sendResponseHeaders(status, answer.length == 0 ? -1 : answer.length);
                if (answer.length > 0) {
                    ex.getResponseBody().write(answer);
                }
            } catch (Exception e) {
                problems.add(e.toString());
                ex.sendResponseHeaders(500, -1);
            } finally {
                ex.close();
            }
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new CloudinaryFileStore("democloud", "12345", secret, "agentx", base + "/v1_1/democloud", base + "/democloud", CLOCK, HttpClient.newHttpClient());
    }

    @Test
    void savesReadsAndDeletesAPrivateFile() throws Exception {
        CloudinaryFileStore store = store("s3cret");
        byte[] bytes = {0, 1, 2, (byte) 200, (byte) 255, 10, 13, 45, 45};
        store.put("chat-media/6f1c2b9e-0000-4000-8000-000000000001", bytes);
        assertThat(objects).containsOnlyKeys("agentx/chat-media/6f1c2b9e-0000-4000-8000-000000000001");
        assertThat(store.get("chat-media/6f1c2b9e-0000-4000-8000-000000000001").orElseThrow()).containsExactly(bytes);
        store.delete("chat-media/6f1c2b9e-0000-4000-8000-000000000001");
        assertThat(store.get("chat-media/6f1c2b9e-0000-4000-8000-000000000001")).isEmpty();
        assertThat(problems).isEmpty();
    }

    @Test
    void aMissingFileIsEmpty() throws Exception {
        assertThat(store("s3cret").get("profile-pictures/none.jpg")).isEmpty();
        assertThat(problems).isEmpty();
    }

    @Test
    void aWrongSecretIsRefusedAndReportedAsAStorageProblem() throws Exception {
        CloudinaryFileStore store = store("wrong");
        assertThatThrownBy(() -> store.put("a/b", new byte[]{1})).isInstanceOf(StorageException.class).hasMessageContaining("401");
        assertThatThrownBy(() -> store.get("a/b")).isInstanceOf(StorageException.class);
    }

    @Test
    void healthyWritesAndReadsBack() throws Exception {
        CloudinaryFileStore store = store("s3cret");
        assertThat(store.healthy()).isTrue();
        assertThat(objects).isEmpty();
        assertThat(store.describe()).contains("democloud").contains("agentx");
    }

    @Test
    void keysAreChecked() throws Exception {
        CloudinaryFileStore store = store("s3cret");
        assertThatThrownBy(() -> store.put("../x", new byte[]{1})).isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> store.get("a//b")).isInstanceOf(StorageException.class);
    }

    @Test
    void theAddressFromTheDashboardIsUnderstood() {
        assertThat(CloudinaryFileStore.fromUrl("cloudinary://12345:s3cret@democloud", "agentx").describe()).contains("democloud");
        assertThatThrownBy(() -> CloudinaryFileStore.fromUrl("https://example.com", "agentx")).hasMessageContaining("cloudinary://");
        assertThatThrownBy(() -> CloudinaryFileStore.fromUrl("cloudinary://nosecret@democloud", "agentx")).hasMessageContaining("cloudinary://");
    }

    @Test
    void missingOrOddSettingsStopTheServiceAtStart() {
        assertThatThrownBy(() -> new CloudinaryFileStore("", "1", "s", "f")).hasMessageContaining("cloud-name");
        assertThatThrownBy(() -> new CloudinaryFileStore("c", "", "s", "f")).hasMessageContaining("api-key");
        assertThatThrownBy(() -> new CloudinaryFileStore("c", "1", "", "f")).hasMessageContaining("api-secret");
        assertThatThrownBy(() -> new CloudinaryFileStore("c", "1", "s", "a b")).hasMessageContaining("folder");
    }
}
