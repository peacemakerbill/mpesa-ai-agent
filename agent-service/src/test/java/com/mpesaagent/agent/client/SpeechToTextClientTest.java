package com.mpesaagent.agent.client;

import com.mpesaagent.agent.config.SpeechProperties;
import com.mpesaagent.agent.exception.UnsupportedFileException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SpeechToTextClientTest {

    private MockRestServiceServer server;
    private SpeechToTextClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://stt.test/v1");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SpeechToTextClient(builder.build(), new SpeechProperties(true, "http://stt.test/v1", "key", "whisper-1", ""));
    }

    @Test
    void oggVoiceNotesAreOggFiles() {
        assertThat(SpeechToTextClient.extensionFor("audio/ogg; codecs=opus")).isEqualTo("ogg");
        assertThat(SpeechToTextClient.extensionFor("AUDIO/MPEG")).isEqualTo("mp3");
        assertThat(SpeechToTextClient.extensionFor("audio/mp4")).isEqualTo("m4a");
    }

    @Test
    void unknownAudioTypesAreRefusedPolitely() {
        assertThatThrownBy(() -> SpeechToTextClient.extensionFor("video/mp4"))
                .isInstanceOf(UnsupportedFileException.class).hasMessageContaining("video/mp4");
        assertThatThrownBy(() -> SpeechToTextClient.extensionFor(null)).isInstanceOf(UnsupportedFileException.class);
    }

    @Test
    void disabledWithoutAKey() {
        SpeechToTextClient off = new SpeechToTextClient(RestClient.create(), new SpeechProperties(true, "http://x", "", "m", ""));
        SpeechToTextClient alsoOff = new SpeechToTextClient(RestClient.create(), new SpeechProperties(false, "http://x", "key", "m", ""));

        assertThat(off.enabled()).isFalse();
        assertThat(alsoOff.enabled()).isFalse();
        assertThat(client.enabled()).isTrue();
    }

    @Test
    void transcriptionIsReturnedTrimmed() {
        server.expect(requestTo("http://stt.test/v1/audio/transcriptions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", org.hamcrest.Matchers.startsWith("multipart/form-data")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("voice.ogg")))
                .andRespond(withSuccess("{\"text\":\"  send five hundred shillings  \"}", MediaType.APPLICATION_JSON));

        assertThat(client.transcribe(new byte[]{1, 2, 3}, "audio/ogg")).contains("send five hundred shillings");
        server.verify();
    }

    @Test
    void silenceGivesNoText() {
        server.expect(requestTo("http://stt.test/v1/audio/transcriptions"))
                .andRespond(withSuccess("{\"text\":\"  \"}", MediaType.APPLICATION_JSON));

        assertThat(client.transcribe(new byte[]{1}, "audio/ogg")).isEmpty();
    }

    @Test
    void aProviderErrorBecomesAFriendlyException() {
        server.expect(requestTo("http://stt.test/v1/audio/transcriptions")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> client.transcribe(new byte[]{1}, "audio/ogg"))
                .isInstanceOf(UnsupportedFileException.class).hasMessageContaining("voice note");
    }
}
