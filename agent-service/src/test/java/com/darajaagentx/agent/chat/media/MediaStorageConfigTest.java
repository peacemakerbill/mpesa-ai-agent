package com.darajaagentx.agent.chat.media;

import com.darajaagentx.agent.store.InMemoryStateStore;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaStorageConfigTest {

    private final MediaStorageConfig config = new MediaStorageConfig();
    private final InMemoryStateStore state = new InMemoryStateStore();

    private MediaBlobStore build(String store, String dir, String bucket) {
        return config.mediaBlobStore(state, store, dir, 30, "", bucket, "us-east-1", "key", "secret", true, "chat");
    }

    @Test
    void picksTheStoreByName() throws IOException {
        String dir = Files.createTempDirectory("cfg").toString();
        assertThat(build("state", dir, "").kind()).isEqualTo("state");
        assertThat(build(" LOCAL ", dir, "").kind()).isEqualTo("local");
        assertThat(build("s3", dir, "my-bucket").kind()).isEqualTo("s3");
    }

    @Test
    void failsLoudlyOnAWrongSetting() {
        assertThatThrownBy(() -> build("ftp", "x", "")).hasMessageContaining("state, local or s3");
        assertThatThrownBy(() -> build("s3", "x", "")).hasMessageContaining("needs bucket");
    }
}
