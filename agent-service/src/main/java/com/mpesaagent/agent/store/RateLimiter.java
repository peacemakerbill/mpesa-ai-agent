package com.mpesaagent.agent.store;

import com.mpesaagent.agent.config.AgentProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/** Stops one phone number from flooding the model (and our bill). Counts messages per clock minute. */
@Component
public class RateLimiter {

    private final StateStore store;
    private final AgentProperties props;

    public RateLimiter(StateStore store, AgentProperties props) {
        this.store = store;
        this.props = props;
    }

    public boolean allow(String phone) {
        long minute = Instant.now().getEpochSecond() / 60;
        long count = store.increment("rate:" + phone + ":" + minute, Duration.ofMinutes(2));
        return count <= props.rateLimitPerMinute();
    }
}
