package com.darajaagentx.api.service;

import com.darajaagentx.api.config.AgentProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Wakes agent-service and says whether it is answering. On a host that puts idle services to sleep (the free plan on
 * Render), the first request starts the service and takes about a minute. Asking for its health is that first request, so
 * the phone app can show "Starting services" and keep asking until this says yes.
 *
 * The ping is short on purpose: it never waits for the service to finish starting, the caller asks again a few seconds
 * later. The answer is remembered for a few seconds, so many phones asking at once make one ping, not many.
 */
@Service
public class AgentWake {

    /** Spring Boot's liveness probe, on by default in agent-service. It needs no key and reveals nothing. */
    static final String HEALTH_PATH = "/actuator/health/liveness";

    private static final Duration KEEP_UP = Duration.ofSeconds(15);
    private static final Duration KEEP_DOWN = Duration.ofSeconds(3);

    private final RestClient client;
    private final Clock clock;

    private volatile boolean up;
    private volatile Instant checkedAt = Instant.EPOCH;

    @Autowired
    public AgentWake(AgentProperties props, Clock clock) {
        this(newClient(props), clock);
    }

    AgentWake(RestClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    private static RestClient newClient(AgentProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        // No internal key here: the health probe is open, and a probe must never carry a secret.
        return RestClient.builder().baseUrl(props.baseUrl()).requestFactory(factory).build();
    }

    /** True when agent-service is answering. False means it is starting, so ask again in a few seconds. */
    public boolean awake() {
        Instant now = clock.instant();
        Duration keep = up ? KEEP_UP : KEEP_DOWN;
        if (now.isBefore(checkedAt.plus(keep))) {
            return up;
        }
        boolean answer = ping();
        up = answer;
        checkedAt = clock.instant();
        return answer;
    }

    private boolean ping() {
        try {
            client.get().uri(HEALTH_PATH).retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            return false;
        }
    }
}
