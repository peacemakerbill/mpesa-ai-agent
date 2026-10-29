package com.mpesaagent.companion.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;

/**
 * Passes a signed-in customer's chat calls to agent-service and hands its answer back unchanged. The customer's
 * number always comes from the login token (the caller passes it in), so nobody can open someone else's chat.
 */
@Service
public class ChatRelay {

    private static final byte[] UNREACHABLE = ("{\"status\":502,\"error\":\"Bad Gateway\","
            + "\"message\":\"The assistant is not reachable right now. Please try again in a moment.\"}").getBytes(StandardCharsets.UTF_8);

    private final RestClient agent;

    public ChatRelay(@Qualifier("agentRestClient") RestClient agent) {
        this.agent = agent;
    }

    public ResponseEntity<byte[]> call(HttpMethod method, String phone, String path, String query, byte[] body,
                                       MediaType contentType, String fileName) {
        try {
            RestClient.RequestBodySpec spec = agent.method(method)
                    .uri("/internal/chat/{phone}" + path + (query == null || query.isBlank() ? "" : "?" + query), phone);
            if (contentType != null) {
                spec.contentType(contentType);
            }
            if (fileName != null && !fileName.isBlank()) {
                spec.header("X-File-Name", fileName);
            }
            if (body != null) {
                spec.body(body);
            }
            return spec.exchange((request, response) -> {
                ResponseEntity.BodyBuilder out = ResponseEntity.status(response.getStatusCode());
                MediaType type = response.getHeaders().getContentType();
                if (type != null) {
                    out.contentType(type);
                }
                String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
                if (disposition != null) {
                    out.header(HttpHeaders.CONTENT_DISPOSITION, disposition);
                }
                return out.body(response.getBody().readAllBytes());
            });
        } catch (RestClientException e) {
            return ResponseEntity.status(502).contentType(MediaType.APPLICATION_JSON).body(UNREACHABLE);
        }
    }
}
