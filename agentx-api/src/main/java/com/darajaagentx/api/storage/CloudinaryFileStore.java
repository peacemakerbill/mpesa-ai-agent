package com.darajaagentx.api.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Keeps files in Cloudinary. Every file is uploaded as a "raw" file, so Cloudinary never resizes or recompresses it,
 * and with delivery type "authenticated", so nobody can open it without a link signed with the API secret. This
 * service downloads files with such signed links and never gives one to a phone.
 *
 * <p>Cloudinary has no keep-until time for a file, so files stay until they are deleted. The keep time that chat
 * files carry is ignored here.
 */
public class CloudinaryFileStore implements FileStore {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryFileStore.class);
    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final Pattern FOLDER = Pattern.compile("^[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+){0,3}$");
    private static final String TYPE = "authenticated";

    private final HttpClient http;
    private final String apiBase;
    private final String deliveryBase;
    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final String folder;
    private final Clock clock;

    public CloudinaryFileStore(String cloudName, String apiKey, String apiSecret, String folder) {
        this(cloudName, apiKey, apiSecret, folder, "https://api.cloudinary.com/v1_1/" + cloudName,
                "https://res.cloudinary.com/" + cloudName, Clock.systemUTC(),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    CloudinaryFileStore(String cloudName, String apiKey, String apiSecret, String folder, String apiBase, String deliveryBase,
                        Clock clock, HttpClient http) {
        if (cloudName == null || !NAME.matcher(cloudName).matches()) {
            throw new StorageException("agentx.storage.cloudinary.cloud-name is missing or not valid.");
        }
        if (apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) {
            throw new StorageException("agentx.storage.cloudinary needs an api-key and an api-secret.");
        }
        String cleanFolder = folder == null ? "" : folder.replaceAll("^/+|/+$", "");
        if (!cleanFolder.isEmpty() && !FOLDER.matcher(cleanFolder).matches()) {
            throw new StorageException("agentx.storage.cloudinary.folder may only hold letters, digits, - and _ between slashes.");
        }
        this.cloudName = cloudName;
        this.apiKey = apiKey.trim();
        this.apiSecret = apiSecret.trim();
        this.folder = cleanFolder;
        this.apiBase = apiBase;
        this.deliveryBase = deliveryBase;
        this.clock = clock;
        this.http = http;
    }

    /** Reads the address Cloudinary shows in its dashboard: cloudinary://API_KEY:API_SECRET@CLOUD_NAME. */
    public static CloudinaryFileStore fromUrl(String url, String folder) {
        try {
            URI uri = URI.create(url.trim());
            String info = uri.getUserInfo();
            if (!"cloudinary".equals(uri.getScheme()) || info == null || !info.contains(":") || uri.getHost() == null) {
                throw new IllegalArgumentException("shape");
            }
            int colon = info.indexOf(':');
            return new CloudinaryFileStore(uri.getHost(), info.substring(0, colon), info.substring(colon + 1), folder);
        } catch (IllegalArgumentException e) {
            throw new StorageException("The Cloudinary address must look like cloudinary://API_KEY:API_SECRET@CLOUD_NAME.");
        }
    }

    @Override
    public void put(String key, byte[] data) {
        String publicId = publicId(key);
        String timestamp = Long.toString(clock.instant().getEpochSecond());
        Map<String, String> signed = new TreeMap<>();
        signed.put("overwrite", "true");
        signed.put("public_id", publicId);
        signed.put("timestamp", timestamp);
        signed.put("type", TYPE);
        String boundary = "----agentx" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream(data.length + 1024);
        try {
            for (Map.Entry<String, String> e : signed.entrySet()) {
                field(body, boundary, e.getKey(), e.getValue());
            }
            field(body, boundary, "api_key", apiKey);
            field(body, boundary, "signature", signature(signed));
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"file\"\r\n"
                    + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(data);
            body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new StorageException("The file could not be prepared for Cloudinary.", e);
        }
        HttpResponse<byte[]> r = send(HttpRequest.newBuilder(URI.create(apiBase + "/raw/upload"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build());
        if (r.statusCode() / 100 != 2) {
            log.warn("Cloudinary refused an upload: http {}", r.statusCode());
            throw new StorageException("Cloudinary did not accept the file (http " + r.statusCode() + ").");
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        String publicId = publicId(key);
        String url = deliveryBase + "/raw/" + TYPE + "/s--" + deliverySignature(publicId) + "--/" + publicId;
        HttpResponse<byte[]> r = send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).GET().build());
        if (r.statusCode() == 404) {
            return Optional.empty();
        }
        if (r.statusCode() / 100 != 2) {
            log.warn("Cloudinary refused a download: http {}", r.statusCode());
            throw new StorageException("Cloudinary is not available right now (http " + r.statusCode() + ").");
        }
        return Optional.of(r.body());
    }

    @Override
    public void delete(String key) {
        String publicId = publicId(key);
        Map<String, String> signed = new TreeMap<>();
        signed.put("invalidate", "true");
        signed.put("public_id", publicId);
        signed.put("timestamp", Long.toString(clock.instant().getEpochSecond()));
        signed.put("type", TYPE);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        String boundary = "----agentx" + UUID.randomUUID().toString().replace("-", "");
        try {
            for (Map.Entry<String, String> e : signed.entrySet()) {
                field(body, boundary, e.getKey(), e.getValue());
            }
            field(body, boundary, "api_key", apiKey);
            field(body, boundary, "signature", signature(signed));
            body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new StorageException("The file could not be removed from Cloudinary.", e);
        }
        HttpResponse<byte[]> r = send(HttpRequest.newBuilder(URI.create(apiBase + "/raw/destroy"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build());
        if (r.statusCode() / 100 != 2) {
            log.warn("Cloudinary refused a delete: http {}", r.statusCode());
            throw new StorageException("The file could not be removed from Cloudinary (http " + r.statusCode() + ").");
        }
    }

    @Override
    public String describe() {
        return "Cloudinary cloud " + cloudName + (folder.isEmpty() ? "" : " folder " + folder);
    }

    /** Writes, reads back and removes a small file. This calls Cloudinary, so it is for a one time check, not for every request. */
    @Override
    public boolean healthy() {
        String key = "health/probe-" + UUID.randomUUID().toString().substring(0, 8);
        byte[] sample = {1, 2, 3};
        try {
            put(key, sample);
            boolean same = get(key).map(b -> Arrays.equals(b, sample)).orElse(false);
            delete(key);
            return same;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private String publicId(String key) {
        FileKeys.require(key);
        return folder.isEmpty() ? key : folder + "/" + key;
    }

    /** SHA-1 of the sorted "name=value" pairs joined with &, followed directly by the API secret, as hex. */
    private String signature(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : new TreeMap<>(params).entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return HexFormat.of().formatHex(sha1(sb + apiSecret));
    }

    /** The first 8 characters of the URL safe base64 of the SHA-1 of the file's public id plus the secret. */
    private String deliverySignature(String publicId) {
        String full = Base64.getUrlEncoder().withoutPadding().encodeToString(sha1(publicId + apiSecret));
        return full.substring(0, 8);
    }

    private static byte[] sha1(String text) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void field(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    private HttpResponse<byte[]> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new StorageException("Cloudinary could not be reached.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StorageException("Cloudinary could not be reached.", e);
        }
    }
}
