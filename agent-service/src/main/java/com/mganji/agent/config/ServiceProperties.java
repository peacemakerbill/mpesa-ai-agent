package com.mganji.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

public final class ServiceProperties {

    private ServiceProperties() {
    }

    @ConfigurationProperties(prefix = "payment")
    public record Payment(@DefaultValue("http://localhost:8083") String baseUrl) {
    }

    /** baseUrl is the private address of ganji-api; publicUrl is what customers' phones open (links in chat). */
    @ConfigurationProperties(prefix = "ganji")
    public record Ganji(@DefaultValue("http://localhost:8084") String baseUrl,
                            @DefaultValue("http://localhost:8084") String publicUrl) {
    }

    @ConfigurationProperties(prefix = "internal")
    public record Internal(String key) {
    }

    @ConfigurationProperties(prefix = "business")
    public record Business(@DefaultValue("Our Business") String name, @DefaultValue("") String supportPhone) {
    }
}
