package com.darajaagentx.agent.chat.media;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalDiskBlobStoreTest {

    @Test
    void storesReadsAndDeletes() throws IOException {
        Path dir = Files.createTempDirectory("media-test");
        LocalDiskBlobStore store = new LocalDiskBlobStore(dir, Duration.ofDays(30));
        String key = UUID.randomUUID().toString();
        store.put(key, new byte[]{1, 2, 3}, Duration.ofDays(1));
        assertThat(Arrays.equals(store.get(key).get(), new byte[]{1, 2, 3})).isTrue();
        store.delete(key);
        assertThat(store.get(key).isPresent()).isEqualTo(false);
        assertThat(store.get(UUID.randomUUID().toString()).isPresent()).isEqualTo(false);
    }

    @Test
    void replacingAFileLeavesNoTemporaryFiles() throws IOException {
        Path dir = Files.createTempDirectory("media-test");
        LocalDiskBlobStore store = new LocalDiskBlobStore(dir, Duration.ofDays(30));
        String key = UUID.randomUUID().toString();
        store.put(key, new byte[]{1}, Duration.ofDays(1));
        store.put(key, new byte[]{9, 9}, Duration.ofDays(1));
        assertThat(Arrays.equals(store.get(key).get(), new byte[]{9, 9})).isTrue();
        try (var files = Files.walk(dir)) {
            assertThat(files.filter(p -> p.getFileName().toString().endsWith(".tmp")).count()).isEqualTo(0L);
        }
    }

    @Test
    void refusesKeysThatCouldLeaveTheFolder() throws IOException {
        LocalDiskBlobStore store = new LocalDiskBlobStore(Files.createTempDirectory("media-test"), Duration.ofDays(30));
        for (String bad : new String[]{"../etc/passwd", "..", "/etc/passwd", "a/b", "", "not-a-uuid", UUID.randomUUID() + "/../x", UUID.randomUUID() + ".sh"}) {
            assertThatThrownBy(() -> store.put(bad, new byte[]{1}, Duration.ofDays(1))).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> store.get(bad)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void sweepRemovesOnlyOldFiles() throws IOException {
        Path dir = Files.createTempDirectory("media-test");
        LocalDiskBlobStore store = new LocalDiskBlobStore(dir, Duration.ofDays(30));
        String old = UUID.randomUUID().toString();
        String fresh = UUID.randomUUID().toString();
        store.put(old, new byte[]{1}, Duration.ofDays(1));
        store.put(fresh, new byte[]{2}, Duration.ofDays(1));
        Files.setLastModifiedTime(store.path(old), FileTime.from(Instant.now().minus(Duration.ofDays(40))));
        assertThat(store.sweep(Instant.now())).isEqualTo(1);
        assertThat(store.get(old).isPresent()).isEqualTo(false);
        assertThat(store.get(fresh).isPresent()).isTrue();
    }
}
