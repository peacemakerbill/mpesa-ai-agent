package com.darajaagentx.agent.chat.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Files in a folder on the server. Point {@code chat.media.local-dir} at a mounted disk or network share to keep them
 * outside the container. Files are spread over 256 sub folders by the first two characters of the id.
 * Writes go to a temporary file first and are moved into place, so a reader never sees half a file.
 * Old files are removed by a sweep that runs at most once an hour, started by the next upload.
 */
public class LocalDiskBlobStore implements MediaBlobStore {

    private static final Logger log = LoggerFactory.getLogger(LocalDiskBlobStore.class);
    private static final long SWEEP_EVERY_MS = Duration.ofHours(1).toMillis();

    private final Path root;
    private final Duration retention;
    private final AtomicLong lastSweep = new AtomicLong();

    public LocalDiskBlobStore(Path root, Duration retention) {
        this.root = root.toAbsolutePath().normalize();
        this.retention = retention;
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create the media folder " + this.root, e);
        }
    }

    @Override
    public String kind() {
        return "local";
    }

    @Override
    public Duration defaultRetention() {
        return retention;
    }

    @Override
    public void put(String key, byte[] bytes, Duration keepFor) {
        Path target = path(key);
        try {
            Files.createDirectories(target.getParent());
            Path tmp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            try {
                Files.write(tmp, bytes);
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store a file", e);
        }
        sweepIfDue();
    }

    @Override
    public Optional<byte[]> get(String key) {
        Path p = path(key);
        try {
            return Files.isRegularFile(p) ? Optional.of(Files.readAllBytes(p)) : Optional.empty();
        } catch (IOException e) {
            log.warn("Could not read a media file: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(path(key));
        } catch (IOException e) {
            log.warn("Could not delete a media file: {}", e.getMessage());
        }
    }

    /** Where a key lives. The key was already checked, and the result is checked again to stay inside the root. */
    Path path(String key) {
        MediaBlobStore.requireSafeKey(key);
        Path p = root.resolve(key.substring(0, 2)).resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Not a valid media key");
        }
        return p;
    }

    /** Removes files older than the retention time. Returns how many were removed. */
    int sweep(Instant now) {
        int removed = 0;
        Instant cutoff = now.minus(retention).minus(Duration.ofHours(1));   // a grace hour, the reader enforces the exact time
        try (Stream<Path> files = Files.walk(root, 2)) {
            for (Path p : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                try {
                    if (Files.getLastModifiedTime(p).toInstant().isBefore(cutoff)) {
                        Files.deleteIfExists(p);
                        removed++;
                    }
                } catch (IOException e) {
                    log.debug("Sweep skipped a file: {}", e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Media sweep failed: {}", e.getMessage());
        }
        return removed;
    }

    private void sweepIfDue() {
        if (retention.isZero() || retention.isNegative()) {
            return;   // keep forever
        }
        long now = System.currentTimeMillis();
        long last = lastSweep.get();
        if (now - last >= SWEEP_EVERY_MS && lastSweep.compareAndSet(last, now)) {
            int n = sweep(Instant.ofEpochMilli(now));
            if (n > 0) {
                log.info("Removed {} expired chat files", n);
            }
        }
    }
}
