package com.darajaagentx.agent.chat.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Files in an S3 compatible bucket over HTTPS. Works with AWS S3, MinIO, Cloudflare R2, DigitalOcean Spaces and Wasabi.
 * Set a lifecycle rule on the bucket to expire old files: this class does not list or sweep.
 *
 * <p>Plain {@code http://} endpoints are accepted for a MinIO on the same private network. Anywhere else use https.
 */
public class S3BlobStore implements MediaBlobStore {

    private static final Logger log = LoggerFactory.getLogger(S3BlobStore.class);

    private final HttpClient http;
    private final SigV4 signer;
    private final String scheme;
    private final String host;
    private final String bucket;
    private final String prefix;
    private final boolean pathStyle;
    private final Duration retention;
    private final Clock clock;

    public S3BlobStore(String endpoint, String bucket, String region, String accessKey, String secretKey,
                       boolean pathStyle, String prefix, Duration retention) {
        this(endpoint, bucket, region, accessKey, secretKey, pathStyle, prefix, retention, Clock.systemUTC(),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    S3BlobStore(String endpoint, String bucket, String region, String accessKey, String secretKey,
                boolean pathStyle, String prefix, Duration retention, Clock clock, HttpClient http) {
        if (bucket == null || bucket.isBlank() || accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("chat.media.s3 needs bucket, access-key and secret-key");
        }
        if (!bucket.matches("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$")) {
            throw new IllegalStateException("chat.media.s3.bucket is not a valid bucket name");
        }
        String cleanPrefix = prefix == null ? "" : prefix.replaceAll("^/+|/+$", "");
        if (!cleanPrefix.matches("^[A-Za-z0-9/_-]*$")) {
            throw new IllegalStateException("chat.media.s3.prefix may only hold letters, digits, / _ and -");
        }
        String ep = endpoint == null || endpoint.isBlank() ? "https://s3." + region + ".amazonaws.com" : endpoint;
        URI uri = URI.create(ep);
        if (uri.getHost() == null || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) {
            throw new IllegalStateException("chat.media.s3.endpoint must be an http(s) address");
        }
        this.scheme = uri.getScheme();
        this.host = uri.getPort() > 0 ? uri.getHost() + ":" + uri.getPort() : uri.getHost();
        this.bucket = bucket;
        this.prefix = cleanPrefix.isEmpty() ? "" : cleanPrefix + "/";
        this.pathStyle = pathStyle;
        this.retention = retention;
        this.clock = clock;
        this.http = http;
        this.signer = new SigV4(accessKey, secretKey, region);
    }

    @Override
    public String kind() {
        return "s3";
    }

    @Override
    public Duration defaultRetention() {
        return retention;
    }

    @Override
    public void put(String key, byte[] bytes, Duration keepFor) {
        HttpResponse<byte[]> r = call("PUT", key, bytes);
        if (r.statusCode() / 100 != 2) {
            log.warn("S3 refused an upload: http {}", r.statusCode());
            throw new IllegalStateException("The file store did not accept the file (http " + r.statusCode() + ")");
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        HttpResponse<byte[]> r = call("GET", key, null);
        if (r.statusCode() == 404) {
            return Optional.empty();
        }
        if (r.statusCode() / 100 != 2) {
            log.warn("S3 refused a download: http {}", r.statusCode());
            throw new IllegalStateException("The file store is not available (http " + r.statusCode() + ")");
        }
        return Optional.of(r.body());
    }

    @Override
    public void delete(String key) {
        HttpResponse<byte[]> r = call("DELETE", key, null);
        if (r.statusCode() / 100 != 2 && r.statusCode() != 404) {
            log.warn("S3 refused a delete: http {}", r.statusCode());
        }
    }

    private HttpResponse<byte[]> call(String method, String key, byte[] body) {
        MediaBlobStore.requireSafeKey(key);
        String objectKey = prefix + key;
        String requestHost = pathStyle ? host : bucket + "." + host;
        String path = pathStyle ? "/" + bucket + "/" + objectKey : "/" + objectKey;
        var now = clock.instant();
        String payloadHash = body == null ? SigV4.EMPTY_SHA256 : SigV4.sha256Hex(body);
        Map<String, String> signed = new LinkedHashMap<>();
        signed.put("host", requestHost);
        signed.put("x-amz-content-sha256", payloadHash);
        signed.put("x-amz-date", SigV4.amzDate(now));
        String authorization = signer.authorization(method, path, "", signed, payloadHash, now);

        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(scheme + "://" + requestHost + path))
                .timeout(Duration.ofSeconds(60))
                .header("x-amz-content-sha256", payloadHash)
                .header("x-amz-date", SigV4.amzDate(now))
                .header("Authorization", authorization);
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        try {
            return http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Could not reach the file store", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while using the file store", e);
        }
    }
}
