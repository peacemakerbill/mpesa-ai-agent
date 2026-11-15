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
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wakes agent-service and says whether it is answering. On a host that puts idle services to sleep (the free plan on
 * Render), the first request starts the service and takes about a minute. Asking for its health is that first request, so
 * the phone app can show "Starting services" and keep asking until this says yes.
 *
 * The ping is short on purpose: the phone's request never waits for the service to finish starting, the caller asks again
 * a few seconds later. The answer is remembered for a few seconds, so many phones asking at once make one ping, not many.
 *
 * A short ping alone does not wake a sleeping service reliably: the host may drop a request that its caller abandoned after
 * five seconds, long before the service is up. So when the short ping gets no answer, one long request is also sent in the
 * background. It stays open until the service answers (a minute or so), which is how a sleeping free service is started
 * and kept starting. Only one long request is open at a time.
 */
@Service
public class AgentWake {

    /** Spring Boot's liveness probe, on by default in agent-service. It needs no key and reveals nothing. */
    static final String HEALTH_PATH = "/actuator/health/liveness";

    private static final Duration KEEP_UP = Duration.ofSeconds(15);
    private static final Duration KEEP_DOWN = Duration.ofSeconds(3);

    /** How long the phone's own question may take. */
    private static final Duration QUICK_READ = Duration.ofSeconds(5);
    /** How long the background request may wait for a sleeping service to start. Cold starts take about a minute. */
    private static final Duration WAKE_READ = Duration.ofSeconds(100);

    private final RestClient client;
    private final RestClient wakeClient;
    private final Clock clock;
    private final Executor background;
    private final AtomicBoolean waking = new AtomicBoolean(false);

    private volatile boolean up;
    private volatile Instant checkedAt = Instant.EPOCH;

    @Autowired
    public AgentWake(AgentProperties props, Clock clock) {
        this(newClient(props, QUICK_READ), newClient(props, WAKE_READ), clock, AgentWake::startThread);
    }

    /** For tests that only look at the short ping: no background request is ever started. */
    AgentWake(RestClient client, Clock clock) {
        this(client, client, clock, task -> { });
    }

    AgentWake(RestClient client, RestClient wakeClient, Clock clock, Executor background) {
        this.client = client;
        this.wakeClient = wakeClient;
        this.clock = clock;
        this.background = background;
    }

    private static void startThread(Runnable task) {
        Thread.ofVirtual().name("agent-wake").start(task);
    }

    private static RestClient newClient(AgentProperties props, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(readTimeout);
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
        boolean answer = ping(client);
        up = answer;
        checkedAt = clock.instant();
        if (!answer) {
            startLongWake();
        }
        return answer;
    }

    /** One long request in the background, so a sleeping service has a request that stays open while it starts. */
    private void startLongWake() {
        if (!waking.compareAndSet(false, true)) {
            return;
        }
        try {
            background.execute(() -> {
                try {
                    boolean answer = ping(wakeClient);
                    up = answer;
                    checkedAt = clock.instant();
                } finally {
                    waking.set(false);
                }
            });
        } catch (RuntimeException e) {
            waking.set(false);
        }
    }

    private static boolean ping(RestClient from) {
        try {
            from.get().uri(HEALTH_PATH).retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            return false;
        }
    }
}
