package com.darajaagentx.api.storage;

import com.darajaagentx.api.model.StoredFile;
import com.darajaagentx.api.repository.StoredFileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Keeps files in the database this service already uses (PostgreSQL, MySQL or MongoDB). It needs no disk and no other
 * account, so it is the way to keep uploads on a host whose disk is wiped on every deploy. It suits pictures and
 * chat sized files, not an archive: the biggest file is limited (MongoDB documents stop at 16 MB) and a free database
 * has little room.
 */
public class DatabaseFileStore implements FileStore {

    private static final Logger log = LoggerFactory.getLogger(DatabaseFileStore.class);
    private static final String PROBE = "health/probe";

    private final StoredFileRepository files;
    private final long maxBytes;
    private final Clock clock;

    public DatabaseFileStore(StoredFileRepository files, long maxBytes, Clock clock) {
        this.files = files;
        this.maxBytes = maxBytes;
        this.clock = clock;
    }

    @Override
    public void put(String key, byte[] data) {
        put(key, data, null);
    }

    @Override
    public void put(String key, byte[] data, Duration keepFor) {
        FileKeys.require(key);
        if (data == null) {
            throw new StorageException("There is no file to save.");
        }
        if (data.length > maxBytes) {
            throw new FileTooLargeException("That file is too large to keep in the database (the limit is "
                    + (maxBytes / (1024 * 1024)) + " MB).");
        }
        Instant now = clock.instant();
        Instant expires = keepFor == null || keepFor.isNegative() || keepFor.isZero() ? null : now.plus(keepFor);
        try {
            files.save(new StoredFile(key, data, now, expires));
        } catch (RuntimeException e) {
            log.warn("The database refused a file of {} bytes: {}", data.length, e.getClass().getSimpleName());
            throw new StorageException("The file could not be saved to the database.", e);
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        FileKeys.require(key);
        try {
            return files.findById(key).filter(f -> !f.expired(clock.instant())).map(StoredFile::getData);
        } catch (RuntimeException e) {
            throw new StorageException("The file could not be read from the database.", e);
        }
    }

    @Override
    public void delete(String key) {
        FileKeys.require(key);
        try {
            if (files.existsById(key)) {
                files.deleteById(key);
            }
        } catch (RuntimeException e) {
            throw new StorageException("The file could not be removed from the database.", e);
        }
    }

    /** Removes files whose keep-until time has passed. Returns how many. */
    public long purgeExpired() {
        return files.deleteByExpiresAtBefore(clock.instant());
    }

    @Override
    public String describe() {
        return "the database (table stored_files, files up to " + (maxBytes / (1024 * 1024)) + " MB)";
    }

    @Override
    public boolean healthy() {
        try {
            files.existsById(PROBE);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
