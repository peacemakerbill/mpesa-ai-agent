package com.mpesaagent.companion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Where agent-service listens. It is private: only this service talks to it. */
@ConfigurationProperties(prefix = "agent")
public record AgentProperties(@DefaultValue("http://localhost:8082") String baseUrl) {
}
