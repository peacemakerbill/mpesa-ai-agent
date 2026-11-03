package com.darajaagentx.agent.chat.media;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * AWS Signature Version 4 for S3 requests, written out in full so the service needs no AWS library.
 * The test checks it against the worked example in the AWS documentation.
 */
final class SigV4 {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    static final String EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private final String accessKey;
    private final String secretKey;
    private final String region;

    SigV4(String accessKey, String secretKey, String region) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.region = region;
    }

    static String amzDate(Instant at) {
        return STAMP.format(at);
    }

    /**
     * The value of the Authorization header.
     *
     * @param path    the URL path as sent, already encoded, starting with "/"
     * @param query   the canonical query string ("" when there is none)
     * @param headers every header that is signed, besides none being left out: must include host, x-amz-content-sha256 and x-amz-date
     */
    String authorization(String method, String path, String query, Map<String, String> headers, String payloadSha256, Instant at) {
        TreeMap<String, String> sorted = new TreeMap<>();
        headers.forEach((k, v) -> sorted.put(k.toLowerCase(), v.trim().replaceAll("\\s+", " ")));
        StringBuilder canonicalHeaders = new StringBuilder();
        StringBuilder names = new StringBuilder();
        for (var e : sorted.entrySet()) {
            canonicalHeaders.append(e.getKey()).append(':').append(e.getValue()).append('\n');
            if (names.length() > 0) {
                names.append(';');
            }
            names.append(e.getKey());
        }
        String canonicalRequest = method + "\n" + path + "\n" + query + "\n" + canonicalHeaders + "\n" + names + "\n" + payloadSha256;
        String day = DAY.format(at);
        String scope = day + "/" + region + "/s3/aws4_request";
        String toSign = "AWS4-HMAC-SHA256\n" + STAMP.format(at) + "\n" + scope + "\n" + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        byte[] key = hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), day);
        key = hmac(key, region);
        key = hmac(key, "s3");
        key = hmac(key, "aws4_request");
        String signature = HexFormat.of().formatHex(hmac(key, toSign));
        return "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + scope + ", SignedHeaders=" + names + ", Signature=" + signature;
    }

    static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
