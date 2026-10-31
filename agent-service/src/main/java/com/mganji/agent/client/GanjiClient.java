package com.mganji.agent.client;

import com.mganji.agent.dto.WalletActionView;
import com.mganji.agent.exception.GanjiApiException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Everything the agent asks ganji-api to do: prepare a request for the customer's own M-Pesa, read its
 * progress, cancel it, and issue the code that links the customer's phone. ganji-api checks that every
 * request belongs to the phone number given, which comes from the signed-in customer, never from the model.
 */
@Component
public class GanjiClient {

    private final RestClient http;
    private final ObjectMapper mapper;

    public GanjiClient(RestClient ganjiRestClient, ObjectMapper mapper) {
        this.http = ganjiRestClient;
        this.mapper = mapper;
    }

    public WalletActionView createAction(Map<String, Object> body) {
        return call(() -> http.post().uri("/internal/actions")
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().onStatus(s -> s.isError(), this::fail).body(WalletActionView.class));
    }

    public List<WalletActionView> list(String phone, int limit) {
        List<WalletActionView> result = call(() -> http.get().uri("/internal/actions?phone={p}&limit={l}", phone, limit)
                .retrieve().onStatus(s -> s.isError(), this::fail)
                .body(new ParameterizedTypeReference<List<WalletActionView>>() {
                }));
        return result == null ? List.of() : result;
    }

    public WalletActionView get(UUID id, String phone) {
        return call(() -> http.get().uri("/internal/actions/{id}?phone={p}", id, phone)
                .retrieve().onStatus(s -> s.isError(), this::fail).body(WalletActionView.class));
    }

    public WalletActionView cancel(UUID id, String phone) {
        return call(() -> http.post().uri("/internal/actions/{id}/cancel", id)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("phone", phone))
                .retrieve().onStatus(s -> s.isError(), this::fail).body(WalletActionView.class));
    }

    public WalletActionView attachReceipt(UUID id, String phone, String receipt) {
        return call(() -> http.post().uri("/internal/actions/{id}/receipt", id)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("phone", phone, "receipt", receipt))
                .retrieve().onStatus(s -> s.isError(), this::fail).body(WalletActionView.class));
    }

    private <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (GanjiApiException e) {
            throw e;
        } catch (RestClientException e) {
            throw new GanjiApiException(0, "The app service is not reachable right now");
        }
    }

    private void fail(org.springframework.http.HttpRequest request,
                      org.springframework.http.client.ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        String message = null;
        try {
            JsonNode body = mapper.readTree(response.getBody().readAllBytes());
            message = body.path("message").asString(null);
        } catch (IOException | JacksonException ignored) {
            // not JSON, use the default below
        }
        if (message == null || message.isBlank()) {
            message = status >= 500 ? "The app service had a problem" : "The request was not accepted";
        }
        throw new GanjiApiException(status, message);
    }
}