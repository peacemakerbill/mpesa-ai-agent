package com.darajaagentx.api.service;

import com.darajaagentx.api.config.DarajaProperties;
import com.darajaagentx.api.exception.DarajaOutcomeUnknownException;
import com.darajaagentx.api.exception.DarajaRefusedException;
import com.darajaagentx.api.model.DarajaEnvironment;
import com.darajaagentx.api.util.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Calls Daraja for a customer's business, with that customer's own consumer key and secret.
 * Access tokens are kept for a short while per key, so a chat with several requests does not ask for a token each time.
 * Keys, secrets and tokens are never logged.
 */
@Component
public class DarajaClient implements DarajaApi {

    private static final Logger log = LoggerFactory.getLogger(DarajaClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };

    private record Token(String value, Instant expires) {
    }

    private final DarajaProperties props;
    private final Clock clock;
    private final ConcurrentHashMap<String, Token> tokens = new ConcurrentHashMap<>();

    public DarajaClient(DarajaProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    @Override
    public Map<String, Object> post(DarajaEnvironment environment, String consumerKey, String consumerSecret, String path, Map<String, Object> body) {
        String token = token(environment, consumerKey, consumerSecret);
        try {
            Map<String, Object> response = client(environment, 20).post().uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve().body(MAP);
            return response == null ? Map.of() : response;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 401) {
                tokens.remove(cacheKey(environment, consumerKey, consumerSecret));
            }
            String text = e.getResponseBodyAsString();
            String message = firstNonBlank(Json.stringField(text, "errorMessage"), Json.stringField(text, "ResponseDescription"),
                    Json.stringField(text, "resultDesc"), Json.stringField(text, "ResultDesc"));
            if (message == null && status >= 500) {
                throw new DarajaOutcomeUnknownException("Safaricom answered with an error (" + status + ") and no explanation.");
            }
            String code = Json.stringField(text, "errorCode");
            throw new DarajaRefusedException((message == null ? "Safaricom did not accept the request (" + status + ")." : message)
                    + (code == null ? "" : " (" + code + ")"));
        } catch (ResourceAccessException e) {
            throw new DarajaOutcomeUnknownException("Safaricom did not answer in time.");
        } catch (RestClientException e) {
            throw new DarajaOutcomeUnknownException("Safaricom's answer could not be read.");
        }
    }

    private String token(DarajaEnvironment environment, String key, String secret) {
        String cacheKey = cacheKey(environment, key, secret);
        Token cached = tokens.get(cacheKey);
        Instant now = clock.instant();
        if (cached != null && now.isBefore(cached.expires())) {
            return cached.value();
        }
        try {
            Map<String, Object> response = client(environment, 10).get()
                    .uri("/oauth/v1/generate?grant_type=client_credentials")
                    .headers(h -> h.setBasicAuth(key, secret))
                    .retrieve().body(MAP);
            Object value = response == null ? null : response.get("access_token");
            if (!(value instanceof String access) || access.isBlank()) {
                throw new DarajaRefusedException("Safaricom did not give an access token.");
            }
            long seconds = 3000;
            Object expires = response.get("expires_in");
            if (expires != null) {
                try {
                    seconds = Math.max(60, Long.parseLong(expires.toString().trim()) - 60);
                } catch (NumberFormatException ignored) {
                    // keep the default
                }
            }
            tokens.put(cacheKey, new Token(access, now.plusSeconds(seconds)));
            return access;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.info("Daraja refused a token request ({}) on {}", status, environment);
            throw new DarajaRefusedException(status == 400 || status == 401
                    ? "Safaricom did not accept the consumer key and secret saved for this account on " + environment.name().toLowerCase() + "."
                    : "Safaricom could not give an access token (" + status + "). Try again in a moment.");
        } catch (RestClientException e) {
            throw new DarajaRefusedException("Could not reach Safaricom. Try again in a moment.");
        }
    }

    private RestClient client(DarajaEnvironment environment, int readSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(readSeconds));
        String base = environment == DarajaEnvironment.PRODUCTION ? props.productionUrl() : props.sandboxUrl();
        return RestClient.builder().requestFactory(factory).baseUrl(base).build();
    }

    /** The cache is keyed by a hash of key and secret together, so a wrong secret never borrows a token made with the right one. */
    private static String cacheKey(DarajaEnvironment environment, String key, String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((environment + "|" + key + "|" + secret).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
