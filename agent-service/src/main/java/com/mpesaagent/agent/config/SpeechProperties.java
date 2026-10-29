package com.mpesaagent.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Speech to text for voice notes (OpenAI compatible /audio/transcriptions). */
@ConfigurationProperties(prefix = "stt")
public record SpeechProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("https://api.openai.com/v1") String baseUrl,
        @DefaultValue("") String apiKey,
        @DefaultValue("whisper-1") String model,
        @DefaultValue("") String language
) {
}
