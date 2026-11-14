package com.darajaagentx.api.storage;

import com.darajaagentx.api.config.StorageProperties;
import com.darajaagentx.api.model.StoredFile;
import com.darajaagentx.api.repository.StoredFileRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseFileStoreTest {

    private final Map<String, StoredFile> rows = new HashMap<>();
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-14T10:00:00Z"));
    private final Clock clock = new Clock() {
        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };
    private boolean broken;

    private StoredFileRepository repo() {
        return (StoredFileRepository) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{StoredFileRepository.class}, (proxy, method, args) -> {
            if (broken && !method.getName().equals("toString")) {
                throw new IllegalStateException("database is down");
            }
            switch (method.getName()) {
                case "save" -> {
                    StoredFile f = (StoredFile) args[0];
                    rows.put(f.getId(), f);
                    return f;
                }
                case "findById" -> {
                    return Optional.ofNullable(rows.get((String) args[0]));
                }
                case "existsById" -> {
                    return rows.containsKey((String) args[0]);
                }
                case "deleteById" -> {
                    rows.remove((String) args[0]);
                    return null;
                }
                case "deleteByExpiresAtBefore" -> {
                    Instant before = (Instant) args[0];
                    long[] count = {0};
                    rows.values().removeIf(f -> {
                        boolean gone = f.getExpiresAt() != null && f.getExpiresAt().isBefore(before);
                        if (gone) {
                            count[0]++;
                        }
                        return gone;
                    });
                    return count[0];
                }
                case "toString" -> {
                    return "fake repository";
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "equals" -> {
                    return proxy == args[0];
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        });
    }

    private DatabaseFileStore store(long max) {
        return new DatabaseFileStore(repo(), max, clock);
    }

    @Test
    void savesReadsAndReplacesAFile() {
        DatabaseFileStore store = store(1000);
        store.put("profile-pictures/a1.jpg", new byte[]{1, 2, 3});
        assertThat(store.get("profile-pictures/a1.jpg").orElseThrow()).containsExactly(1, 2, 3);
        store.put("profile-pictures/a1.jpg", new byte[]{9});
        assertThat(store.get("profile-pictures/a1.jpg").orElseThrow()).containsExactly(9);
        assertThat(rows).hasSize(1);
        assertThat(rows.get("profile-pictures/a1.jpg").getSizeBytes()).isEqualTo(1);
    }

    @Test
    void aMissingFileIsEmptyAndDeletingItIsFine() {
        DatabaseFileStore store = store(1000);
        assertThat(store.get("nothing/here")).isEmpty();
        store.delete("nothing/here");
        store.put("x/y", new byte[]{1});
        store.delete("x/y");
        assertThat(store.get("x/y")).isEmpty();
    }

    @Test
    void aFileWithAKeepTimeDisappearsAfterIt() {
        DatabaseFileStore store = store(1000);
        store.put("chat-media/f1", new byte[]{1}, Duration.ofHours(1));
        store.put("chat-media/f2", new byte[]{2});
        now.set(now.get().plus(Duration.ofMinutes(59)));
        assertThat(store.get("chat-media/f1")).isPresent();
        now.set(now.get().plus(Duration.ofMinutes(2)));
        assertThat(store.get("chat-media/f1")).isEmpty();
        assertThat(store.get("chat-media/f2")).isPresent();
        assertThat(store.purgeExpired()).isEqualTo(1);
        assertThat(rows).containsOnlyKeys("chat-media/f2");
    }

    @Test
    void aTooBigFileIsRefusedWithAClearMessage() {
        DatabaseFileStore store = store(10 * 1024 * 1024);
        assertThatThrownBy(() -> store.put("a/b", new byte[10 * 1024 * 1024 + 1]))
                .isInstanceOf(FileTooLargeException.class).hasMessageContaining("10 MB");
        assertThat(rows).isEmpty();
    }

    @Test
    void keysThatCouldEscapeAreRefused() {
        DatabaseFileStore store = store(1000);
        for (String bad : new String[]{"../x", "/etc/passwd", "a//b", "a/../b", "", ".hidden", "a b"}) {
            assertThatThrownBy(() -> store.put(bad, new byte[]{1})).isInstanceOf(StorageException.class);
            assertThatThrownBy(() -> store.get(bad)).isInstanceOf(StorageException.class);
        }
        assertThatThrownBy(() -> store.put(null, new byte[]{1})).isInstanceOf(StorageException.class);
    }

    @Test
    void aDatabaseProblemBecomesAStorageException() {
        DatabaseFileStore store = store(1000);
        broken = true;
        assertThatThrownBy(() -> store.put("a/b", new byte[]{1})).isInstanceOf(StorageException.class).hasMessageContaining("database");
        assertThatThrownBy(() -> store.get("a/b")).isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> store.delete("a/b")).isInstanceOf(StorageException.class);
        assertThat(store.healthy()).isFalse();
    }

    @Test
    void healthyWhenTheDatabaseAnswers() {
        DatabaseFileStore store = store(1000);
        assertThat(store.healthy()).isTrue();
        assertThat(store.describe()).contains("database").contains("stored_files");
    }

    @Test
    void theSettingPicksTheDatabaseStoreAndNeedsADatabase() {
        StorageProperties props = new StorageProperties(" Database ", new StorageProperties.Local("./x"), new StorageProperties.Network("", false, "a"),
                new StorageProperties.Database(1_048_576), new StorageProperties.Cloudinary("", "", "", "", "agentx"), 3_145_728);
        assertThat(StorageConfig.create(props, repo())).isInstanceOf(DatabaseFileStore.class);
        assertThatThrownBy(() -> StorageConfig.create(props)).hasMessageContaining("no database");
    }

    @Test
    void thePurgerOnlyActsOnADatabaseStore() {
        DatabaseFileStore store = store(1000);
        store.put("chat-media/f1", new byte[]{1}, Duration.ofMinutes(1));
        now.set(now.get().plus(Duration.ofMinutes(5)));
        new FilePurger(store).run();
        assertThat(rows).isEmpty();
        new FilePurger(new FileStore() {
            @Override
            public void put(String key, byte[] data) {
                throw new AssertionError("not used");
            }

            @Override
            public Optional<byte[]> get(String key) {
                return Optional.empty();
            }

            @Override
            public void delete(String key) {
                throw new AssertionError("not used");
            }

            @Override
            public String describe() {
                return "other";
            }

            @Override
            public boolean healthy() {
                return true;
            }
        }).run();   // does nothing, and does not fail
    }
}
