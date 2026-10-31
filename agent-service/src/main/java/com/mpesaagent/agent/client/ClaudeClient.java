package com.mpesaagent.agent.client;

import com.mpesaagent.agent.exception.ModelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Set;

/**
 * Calls the Claude Messages API (POST /v1/messages). Rate limits (429), overload (529) and server errors are
 * retried a few times with a growing pause. Anything else is a bug in our request and fails at once.
 */
@Component
public class ClaudeClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeClient.class);
    private static final Set<Integer> RETRYABLE = Set.of(408, 429, 500, 502, 503, 504, 529);
    private static final int ATTEMPTS = 3;

    private final RestClient http;
    private final Sleeper sleeper;

    public ClaudeClient(RestClient anthropicRestClient) {
        this(anthropicRestClient, Thread::sleep);
    }

    /** Lets tests skip the waiting. */
    ClaudeClient(RestClient http, Sleeper sleeper) {
        this.http = http;
        this.sleeper = sleeper;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    public JsonNode createMessage(ObjectNode body) {
        ModelException last = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                JsonNode response = http.post()
                        .uri("/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (req, res) -> {
                            int status = res.getStatusCode().value();
                            String detail = new String(res.getBody().readNBytes(2000));
                            throw new HttpFailure(status, detail);
                        })
                        .body(JsonNode.class);
                if (response == null || !response.has("content")) {
                    throw new ModelException("The model returned an empty answer");
                }
                return response;
            } catch (HttpFailure f) {
                last = new ModelException("Claude API error " + f.status + ": " + f.detail);
                if (!RETRYABLE.contains(f.status) || attempt == ATTEMPTS) {
                    throw last;
                }
                log.warn("Claude API returned {} (attempt {} of {}), retrying", f.status, attempt, ATTEMPTS);
                pause(attempt);
            } catch (RestClientException e) {
                last = new ModelException("Could not reach the Claude API", e);
                if (attempt == ATTEMPTS) {
                    throw last;
                }
                log.warn("Claude API unreachable (attempt {} of {}), retrying", attempt, ATTEMPTS);
                pause(attempt);
            }
        }
        throw last == null ? new ModelException("The model did not answer") : last;
    }

    private void pause(int attempt) {
        try {
            sleeper.sleep(1000L * (1L << (attempt - 1)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelException("Interrupted while waiting to retry");
        }
    }

    /** Carries the HTTP status out of the status handler (which may only throw unchecked exceptions). */
    @SuppressWarnings("serial")
	private static final class HttpFailure extends RuntimeException {
        final int status;
        final String detail;

        HttpFailure(int status, String detail) {
            super("HTTP " + status, null, false, false);
            this.status = status;
            this.detail = detail;
        }
    }
}