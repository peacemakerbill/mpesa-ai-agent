package com.darajaagentx.api.storage;

import com.darajaagentx.api.config.StorageProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The local and network file stores, on real folders. */
class DiskFileStoreTest {

    private static Path temp() throws Exception {
        return Files.createTempDirectory("agentx-store-test");
    }

    private static StorageProperties props(String type, Path local, Path network, boolean create) {
        return new StorageProperties(type, new StorageProperties.Local(local.toString()), new StorageProperties.Network(network == null ? "" : network.toString(), create, "agentx-api"), 3_145_728);
    }

    @Test
    void savesReadsAndDeletes() throws Exception {
        Path root = temp();
        FileStore store = DiskFileStore.local(root.resolve("files"));
        store.put("profile-pictures/abc-1.jpg", new byte[]{1, 2, 3});
        assertThat(store.get("profile-pictures/abc-1.jpg").orElseThrow()).isEqualTo(new byte[]{1, 2, 3});
        store.put("profile-pictures/abc-1.jpg", new byte[]{9});
        assertThat(Arrays.toString(store.get("profile-pictures/abc-1.jpg").orElseThrow())).isEqualTo("[9]");
        assertThat(store.get("profile-pictures/none.jpg").isPresent()).isFalse();
        store.delete("profile-pictures/abc-1.jpg");
        store.delete("profile-pictures/abc-1.jpg");           // again is not an error
        assertThat(store.get("profile-pictures/abc-1.jpg").isPresent()).isFalse();
        assertThat(store.healthy()).isTrue();
        // no half written or probe files are left behind
        try (var files = Files.walk(root.resolve("files"))) {
            assertThat(files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".tmp") || p.getFileName().toString().startsWith(".health")).count()).isEqualTo(0L);
        }
    }

    @Test
    void aKeyCanNeverLeaveTheFolder() throws Exception {
        FileStore store = DiskFileStore.local(temp().resolve("files"));
        for (String bad : new String[]{"../x", "a/../../x", "/etc/passwd", "a//b", "..", ".hidden", "a/.b", "a\\b", "a b", "", "a/b/c/d/e", "x".repeat(200)}) {
            assertThatThrownBy(() -> store.put(bad, new byte[]{1})).hasMessageContaining("not allowed");
            assertThatThrownBy(() -> store.get(bad)).hasMessageContaining("not allowed");
        }
        assertThatThrownBy(() -> store.put(null, new byte[]{1})).hasMessageContaining("not allowed");
    }

    @Test
    void theLocalTypeMakesTheFolder() throws Exception {
        Path base = temp();
        FileStore store = StorageConfig.create(props("local", base.resolve("a/b/c"), null, false));
        assertThat(store.describe()).startsWith("local folder ");
        assertThat(Files.isDirectory(base.resolve("a/b/c"))).isTrue();
        assertThat(StorageConfig.create(props(" LOCAL ", base.resolve("d"), null, false)).describe()).startsWith("local folder ");
    }

    @Test
    void theNetworkTypeUsesAMountedShareAndRefusesAMissingOne() throws Exception {
        Path base = temp();
        Path mount = base.resolve("mnt-agentx");
        assertThatThrownBy(() -> StorageConfig.create(props("network", base.resolve("local"), mount, false)))
                .hasMessageContaining("does not exist").hasMessageContaining("Mount the share");
        assertThat(Files.exists(base.resolve("local"))).isFalse();       // nothing quietly written to the local folder
        assertThatThrownBy(() -> StorageConfig.create(props("network", base.resolve("local"), null, false))).hasMessageContaining("network.path is empty");

        Files.createDirectories(mount);                                    // the share is mounted
        FileStore store = StorageConfig.create(props("network", base.resolve("local"), mount, false));
        assertThat(store.describe()).startsWith("network share ");
        store.put("profile-pictures/p-1.jpg", new byte[]{7});
        assertThat(Files.exists(mount.resolve("agentx-api/profile-pictures/p-1.jpg"))).isTrue();

        FileStore created = StorageConfig.create(props("network", base.resolve("local"), base.resolve("new-mount"), true));
        assertThat(created.healthy()).isTrue();
    }

    @Test
    void anUnknownTypeStopsTheServiceWithAClearMessage() throws Exception {
        assertThatThrownBy(() -> StorageConfig.create(props("s3", temp(), null, false))).hasMessageContaining("local or network");
    }

    @Test
    void aFolderThatCannotBeWrittenIsReported() throws Exception {
        Path base = temp();
        Path file = base.resolve("a-file");
        Files.write(file, new byte[]{1});
        assertThatThrownBy(() -> DiskFileStore.local(file.resolve("inside"))).hasMessageContaining("cannot be created");
    }
}
