package com.mganji.agent.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "anthropic")
public record AnthropicProperties(
        @NotBlank String apiKey,
        @DefaultValue("https://api.anthropic.com") String baseUrl,
        @DefaultValue("claude-sonnet-5-5") String model,
        @DefaultValue("1024") int maxTokens,
        @DefaultValue("2023-06-01") String version
) {
}
