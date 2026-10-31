package com.mganji.agent.client;

import com.mganji.agent.config.SpeechProperties;
import com.mganji.agent.exception.UnsupportedFileException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Optional;

/**
 * Turns a voice note into text through any OpenAI compatible endpoint (POST {base-url}/audio/transcriptions).
 * The audio is sent to that provider, so switch this on only after choosing a provider you are allowed to use
 * for customers' data.
 */
@Component
public class SpeechToTextClient {

    private static final Logger log = LoggerFactory.getLogger(SpeechToTextClient.class);

    /** Browsers record webm (Chrome, Firefox) or mp4 (Safari), phones record m4a or aac. Ogg and mp3 also work. */
    private static final Map<String, String> EXTENSIONS = Map.of(
            "audio/ogg", "ogg",
            "audio/opus", "ogg",
            "audio/mpeg", "mp3",
            "audio/mp3", "mp3",
            "audio/mp4", "m4a",
            "audio/x-m4a", "m4a",
            "audio/aac", "m4a",
            "audio/wav", "wav",
            "audio/webm", "webm",
            "audio/flac", "flac");

    private final RestClient http;
    private final SpeechProperties props;

    public SpeechToTextClient(RestClient sttRestClient, SpeechProperties props) {
        this.http = sttRestClient;
        this.props = props;
    }

    public boolean enabled() {
        return props.enabled() && !props.apiKey().isBlank();
    }

    /** Returns the words spoken, or empty when nothing intelligible was said. */
    public Optional<String> transcribe(byte[] audio, String mimeType) {
        String extension = extensionFor(mimeType);
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("model", props.model());
        form.add("response_format", "json");
        if (props.language() != null && !props.language().isBlank()) {
            form.add("language", props.language());
        }
        form.add("file", new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return "voice." + extension;
            }
        });
        try {
            JsonNode response = http.post().uri("/audio/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            String text = response == null ? "" : response.path("text").asString("").strip();
            return text.isEmpty() ? Optional.empty() : Optional.of(text);
        } catch (RestClientException e) {
            log.error("Speech to text failed: {}", e.getMessage());
            throw new UnsupportedFileException("I could not process that voice note right now", e);
        }
    }

    static String extensionFor(String mimeType) {
        String base = mimeType == null ? "" : mimeType.split(";")[0].trim().toLowerCase();
        String extension = EXTENSIONS.get(base);
        if (extension == null) {
            throw new UnsupportedFileException("I cannot play this kind of audio (" + (base.isEmpty() ? "unknown" : base) + ")");
        }
        return extension;
    }
}