package com.darajaagentx.agent.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calls agentx-api's /internal/business endpoints. It builds its own HTTP client from the address and internal key the
 * agent already has, so it works whatever the other clients are called. The phone number always comes from the signed in
 * customer, never from the model.
 */
@Component
public class AgentXBusinessClient implements BusinessApi {

    private static final Pattern MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private final RestClient http;

    public AgentXBusinessClient(@Value("${agentx.base-url:${companion.base-url:http://localhost:8084}}") String baseUrl,
                               @Value("${internal.key}") String internalKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        // Sending a request to Safaricom happens inside the call, so allow longer than a normal lookup
        factory.setReadTimeout(Duration.ofSeconds(40));
        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Key", internalKey)
                .requestFactory(factory)
                .build();
    }

    @Override
    public List<Map<String, Object>> accounts(String phone) {
        List<Map<String, Object>> list = call(() -> http.get().uri("/internal/business/accounts?phone={p}", phone)
                .retrieve().onStatus(s -> s.isError(), this::fail)
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                }));
        return list == null ? List.of() : list;
    }

    @Override
    public Map<String, Object> prepare(Map<String, Object> body) {
        return call(() -> http.post().uri("/internal/business/operations")
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().onStatus(s -> s.isError(), this::fail)
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                }));
    }

    @Override
    public List<Map<String, Object>> operations(String phone, int limit) {
        List<Map<String, Object>> list = call(() -> http.get().uri("/internal/business/operations?phone={p}&limit={l}", phone, limit)
                .retrieve().onStatus(s -> s.isError(), this::fail)
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                }));
        return list == null ? List.of() : list;
    }

    @Override
    public Map<String, Object> operation(String id, String phone) {
        return call(() -> http.get().uri("/internal/business/operations/{id}?phone={p}", id, phone)
                .retrieve().onStatus(s -> s.isError(), this::fail)
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                }));
    }

    @Override
    public Map<String, Object> cancel(String id, String phone) {
        return call(() -> http.post().uri("/internal/business/operations/{id}/cancel", id)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("phone", phone))
                .retrieve().onStatus(s -> s.isError(), this::fail)
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                }));
    }

    private <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (BusinessApiException e) {
            throw e;
        } catch (RestClientException e) {
            throw new BusinessApiException(0, "The business accounts service is not reachable right now");
        }
    }

    private void fail(org.springframework.http.HttpRequest request, org.springframework.http.client.ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        String message = null;
        try {
            Matcher m = MESSAGE.matcher(new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8));
            if (m.find()) {
                message = m.group(1).replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\");
            }
        } catch (IOException ignored) {
            // no readable body, use the default below
        }
        if (message == null || message.isBlank()) {
            message = status >= 500 ? "The business accounts service had a problem" : "The request was not accepted";
        }
        throw new BusinessApiException(status, message);
    }
}
