package com.darajaagentx.agent.chat;

import com.darajaagentx.agent.chat.media.MediaBlobStore;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatMediaTest {

    /** A store in memory that can be told to fail, so the ordering of writes can be checked. */
    static class MapStore implements MediaBlobStore {
        final Map<String, byte[]> data = new HashMap<>();
        final String kind;
        boolean failOnMeta;

        MapStore(String kind) {
            this.kind = kind;
        }

        public String kind() { return kind; }
        public Duration defaultRetention() { return "state".equals(kind) ? Duration.ofHours(24) : Duration.ofDays(30); }
        public void put(String key, byte[] bytes, Duration keepFor) {
            if (failOnMeta && key.endsWith(".meta")) {
                throw new IllegalStateException("disk full");
            }
            data.put(key, bytes);
        }
        public Optional<byte[]> get(String key) { return Optional.ofNullable(data.get(key)); }
        public void delete(String key) { data.remove(key); }
    }

    private final MapStore blobs = new MapStore("local");
    private final ChatMedia media = new ChatMedia(blobs);

    @Test
    void onlyTheOwnerCanReadAFile() {
        String id = media.put("254712345678", new byte[]{1, 2}, "image/png", "photo one.png");
        ChatMedia.Found own = media.get("254712345678", id).get();
        assertThat(Arrays.equals(own.bytes(), new byte[]{1, 2})).isTrue();
        assertThat(own.mimeType()).isEqualTo("image/png");
        assertThat(own.fileName()).isEqualTo("photo one.png");
        assertThat(media.get("254799999999", id).isPresent()).isEqualTo(false);
        assertThat(media.exists("254712345678", id)).isTrue();
    }

    @Test
    void ignoresIdsThatAreNotIds() {
        assertThat(media.get("254712345678", "../../etc/passwd").isPresent()).isEqualTo(false);
        assertThat(media.get("254712345678", null).isPresent()).isEqualTo(false);
        assertThat(media.get(null, UUID.randomUUID().toString()).isPresent()).isEqualTo(false);
    }

    @Test
    void aFileWithoutANameAndAnUnknownTypeStillWork() {
        String id = media.put("254712345678", new byte[]{3}, null, null);
        ChatMedia.Found f = media.get("254712345678", id).get();
        assertThat(f.mimeType()).isEqualTo("application/octet-stream");
        assertThat(f.fileName() == null).isTrue();
    }

    @Test
    void oddCharactersInNamesSurvive() {
        String name = "receipt\nline éè 文件.pdf";
        String id = media.put("254712345678", new byte[]{3}, "application/pdf", name);
        assertThat(media.get("254712345678", id).get().fileName()).isEqualTo(name);
    }

    @Test
    void expiredFilesAreGoneAndRemoved() {
        String id = UUID.randomUUID().toString();
        blobs.data.put(id, new byte[]{1});
        blobs.data.put(id + ".meta", ChatMedia.encode(new ChatMedia.Meta("254712345678", "image/png", null, 1, Instant.now().minus(Duration.ofDays(31))))
                .getBytes(StandardCharsets.UTF_8));
        assertThat(media.get("254712345678", id).isPresent()).isEqualTo(false);
        assertThat(blobs.data.isEmpty()).isTrue();
    }

    @Test
    void retentionZeroKeepsFilesUntilDeleted() {
        ChatMedia forever = new ChatMedia(blobs, 0);
        String id = UUID.randomUUID().toString();
        blobs.data.put(id, new byte[]{1});
        blobs.data.put(id + ".meta", ChatMedia.encode(new ChatMedia.Meta("254712345678", "image/png", null, 1, Instant.now().minus(Duration.ofDays(900))))
                .getBytes(StandardCharsets.UTF_8));
        assertThat(forever.get("254712345678", id).isPresent()).isTrue();
    }

    @Test
    void theStateStoreKeepsItsOneDayRuleWhateverTheSetting() {
        ChatMedia state = new ChatMedia(new MapStore("state"), 90);
        String id = UUID.randomUUID().toString();
        MapStore s = new MapStore("state");
        ChatMedia c = new ChatMedia(s, 90);
        s.data.put(id, new byte[]{1});
        s.data.put(id + ".meta", ChatMedia.encode(new ChatMedia.Meta("254712345678", "image/png", null, 1, Instant.now().minus(Duration.ofHours(25))))
                .getBytes(StandardCharsets.UTF_8));
        assertThat(c.get("254712345678", id).isPresent()).isEqualTo(false);
        assertThat(state.storeKind()).isEqualTo("state");
    }

    @Test
    void removeOnlyWorksForTheOwner() {
        String id = media.put("254712345678", new byte[]{1}, "image/png", null);
        assertThat(media.remove("254799999999", id)).isEqualTo(false);
        assertThat(media.exists("254712345678", id)).isTrue();
        assertThat(media.remove("254712345678", id)).isTrue();
        assertThat(blobs.data.isEmpty()).isTrue();
        assertThat(media.remove("254712345678", id)).isEqualTo(false);
    }

    @Test
    void aFailedNoteDoesNotLeaveAnOrphanedFile() {
        blobs.failOnMeta = true;
        assertThatThrownBy(() -> media.put("254712345678", new byte[]{1}, "image/png", null)).hasMessageContaining("disk full");
        assertThat(blobs.data.isEmpty()).isTrue();
    }

    @Test
    void aDamagedNoteReadsAsMissing() {
        String id = UUID.randomUUID().toString();
        blobs.data.put(id, new byte[]{1});
        blobs.data.put(id + ".meta", "garbage".getBytes(StandardCharsets.UTF_8));
        assertThat(media.get("254712345678", id).isPresent()).isEqualTo(false);
    }
}
