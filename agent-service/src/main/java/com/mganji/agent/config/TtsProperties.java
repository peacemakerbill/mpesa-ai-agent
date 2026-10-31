package com.mganji.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Optional spoken replies (OpenAI compatible /audio/speech). */
@ConfigurationProperties(prefix = "tts")
public record TtsProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("https://api.openai.com/v1") String baseUrl,
        @DefaultValue("") String apiKey,
        @DefaultValue("tts-1") String model,
        @DefaultValue("alloy") String voice
) {
}
