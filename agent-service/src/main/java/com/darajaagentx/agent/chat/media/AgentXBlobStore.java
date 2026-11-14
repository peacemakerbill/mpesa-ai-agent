package com.darajaagentx.agent.chat.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Optional;

/**
 * Keeps chat files in agentx-api, over its internal files address. agentx-api puts them wherever it keeps files: a
 * folder, its database or Cloudinary. This is the way to have uploads that survive a restart when this service has no disk
 * and no file service of its own, because the database already belongs to agentx-api.
 */
public class AgentXBlobStore implements MediaBlobStore {

    private static final Logger log = LoggerFactory.getLogger(AgentXBlobStore.class);
    private static final String PATH = "/internal/files/chat-media/{key}";

    private final RestClient http;
    private final Duration retention;

    public AgentXBlobStore(RestClient agentxRestClient, Duration retention) {
        this.http = agentxRestClient;
        this.retention = retention;
    }

    @Override
    public String kind() {
        return "agentx";
    }

    @Override
    public Duration defaultRetention() {
        return retention;
    }

    @Override
    public void put(String key, byte[] bytes, Duration keepFor) {
        MediaBlobStore.requireSafeKey(key);
        long seconds = keepFor == null ? 0 : Math.max(0, keepFor.toSeconds());
        try {
            http.put().uri(PATH + "?keepSeconds={s}", key, seconds)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM).body(bytes)
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.warn("agentx-api refused a file: http {}", e.getStatusCode().value());
            if (e.getStatusCode().value() == 413) {
                throw new IllegalStateException("That file is too large for the file store");
            }
            throw new IllegalStateException("The file store did not accept the file (http " + e.getStatusCode().value() + ")");
        } catch (RestClientException e) {
            throw new IllegalStateException("The file store is not reachable right now");
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        MediaBlobStore.requireSafeKey(key);
        try {
            return Optional.ofNullable(http.get().uri(PATH, key).retrieve().body(byte[].class));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            log.warn("agentx-api refused a file download: http {}", e.getStatusCode().value());
            throw new IllegalStateException("The file store is not available (http " + e.getStatusCode().value() + ")");
        } catch (RestClientException e) {
            throw new IllegalStateException("The file store is not reachable right now");
        }
    }

    @Override
    public void delete(String key) {
        MediaBlobStore.requireSafeKey(key);
        try {
            http.delete().uri(PATH, key).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Could not remove a file from agentx-api: {}", e.getClass().getSimpleName());
        }
    }
}
