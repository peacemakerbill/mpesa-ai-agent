package com.mpesaagent.agent;

import com.mpesaagent.agent.config.AgentProperties;
import com.mpesaagent.agent.config.ServiceProperties;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

/** Shared helpers for tests. */
public final class TestSupport {

    private TestSupport() {
    }

    /** A plain Jackson 3 mapper, like Spring Boot's: java.time and parameter names are built in. */
    public static ObjectMapper mapper() {
        return JsonMapper.builder().build();
    }

    public static AgentProperties props() {
        return new AgentProperties("memory", 20, Duration.ofHours(24), 5, 6, Duration.ofMinutes(10), Duration.ofHours(24),
                List.of("254711000003"), 5_242_880L, 15_728_640L, 16_777_216L, 20_971_520L, 20_000, true);
    }

    /** The default in production: only the customer wallet tools, no business payment tools. */
    public static AgentProperties walletProps() {
        AgentProperties p = props();
        return new AgentProperties(p.store(), p.historySize(), p.historyTtl(), p.rateLimitPerMinute(), p.maxToolRounds(), p.intentTtl(),
                p.approvalIntentTtl(), p.escalationPhones(), p.maxImageBytes(), p.maxDocumentBytes(), p.maxAudioBytes(),
                p.maxUploadBytes(), p.maxTextChars(), false);
    }

    public static ServiceProperties.Business business() {
        return new ServiceProperties.Business("Test Shop", "0700000000");
    }
}