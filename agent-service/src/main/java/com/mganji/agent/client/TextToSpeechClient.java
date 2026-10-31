package com.mganji.agent.client;

import com.mganji.agent.config.TtsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.Optional;

/** Optional: speaks a short reply (POST {base-url}/audio/speech) as an mp3 voice note. Off by default. */
@Component
public class TextToSpeechClient {

    private static final Logger log = LoggerFactory.getLogger(TextToSpeechClient.class);

    private final RestClient http;
    private final TtsProperties props;

    public TextToSpeechClient(RestClient ttsRestClient, TtsProperties props) {
        this.http = ttsRestClient;
        this.props = props;
    }

    public boolean enabled() {
        return props.enabled() && !props.apiKey().isBlank();
    }

    /** Ogg Opus bytes, or empty if speech could not be produced (a spoken reply is a nicety, never required). */
    public Optional<byte[]> speak(String text) {
        try {
            byte[] audio = http.post().uri("/audio/speech")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", props.model(), "voice", props.voice(), "input", text, "response_format", "mp3"))
                    .retrieve()
                    .body(byte[].class);
            return audio == null || audio.length == 0 ? Optional.empty() : Optional.of(audio);
        } catch (RestClientException e) {
            log.warn("Text to speech failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
