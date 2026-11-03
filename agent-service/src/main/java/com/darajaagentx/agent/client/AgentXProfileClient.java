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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calls agentx-api's /internal/profile and /internal/email endpoints. Like the business client it builds its own HTTP client
 * from the address and internal key the agent already has. The phone number always comes from the signed in customer.
 */
@Component
public class AgentXProfileClient implements ProfileApi {

    private static final Pattern MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private final RestClient http;

    public AgentXProfileClient(@Value("${agentx.base-url:${companion.base-url:http://localhost:8084}}") String baseUrl,
                               @Value("${internal.key}") String internalKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        // Building a report and handing it to the mail server happens inside the call
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Key", internalKey)
                .requestFactory(factory)
                .build();
    }

    @Override
    public Map<String, Object> profile(String phone) {
        return call(() -> http.get().uri("/internal/profile?phone={p}", phone)
                .retrieve().onStatus(s -> s.isError(), this::fail).body(MAP));
    }

    @Override
    public Map<String, Object> updateProfile(String phone, Map<String, Object> changes) {
        return call(() -> http.put().uri("/internal/profile?phone={p}", phone)
                .contentType(MediaType.APPLICATION_JSON).body(changes)
                .retrieve().onStatus(s -> s.isError(), this::fail).body(MAP));
    }

    @Override
    public Map<String, Object> requestEmailCode(String phone, String email) {
        return call(() -> http.post().uri("/internal/profile/email/request-code?phone={p}", phone)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("email", email))
                .retrieve().onStatus(s -> s.isError(), this::fail).body(MAP));
    }

    @Override
    public Map<String, Object> verifyEmail(String phone, String code) {
        return call(() -> http.post().uri("/internal/profile/email/verify?phone={p}", phone)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("code", code))
                .retrieve().onStatus(s -> s.isError(), this::fail).body(MAP));
    }

    @Override
    public List<Map<String, Object>> recipients(String phone) {
        List<Map<String, Object>> list = call(() -> http.get().uri("/internal/email/recipients?phone={p}", phone)
                .retrieve().onStatus(s -> s.isError(), this::fail)
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                }));
        return list == null ? List.of() : list;
    }

    @Override
    public Map<String, Object> addRecipient(String phone, String email, String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("name", name);
        return call(() -> http.post().uri("/internal/email/recipients?phone={p}", phone)
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().onStatus(s -> s.isError(), this::fail).body(MAP));
    }

    @Override
    public Map<String, Object> addRecipients(String phone, String text) {
        return call(() -> http.post().uri("/internal/email/recipients/paste?phone={p}", phone)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("text", text))
                .retrieve().onStatus(s -> s.isError(), this::fail).body(MAP));
    }

    @Override
    public Map<String, Object> removeRecipient(String phone, String target) {
        return call(() -> http.delete().uri("/internal/email/recipients?phone={p}&target={t}", phone, target)
                .retrieve().onStatus(s -> s.isError(), this::fail).body(MAP));
    }

    @Override
    public Map<String, Object> sendReport(String phone, Map<String, Object> request) {
        return call(() -> http.post().uri("/internal/email/report?phone={p}", phone)
                .contentType(MediaType.APPLICATION_JSON).body(request)
                .retrieve().onStatus(s -> s.isError(), this::fail).body(MAP));
    }

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };

    private <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (ProfileApiException e) {
            throw e;
        } catch (RestClientException e) {
            throw new ProfileApiException(0, "The profile service is not reachable right now");
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
            message = status >= 500 ? "The profile service had a problem" : "The request was not accepted";
        }
        throw new ProfileApiException(status, message);
    }
}
