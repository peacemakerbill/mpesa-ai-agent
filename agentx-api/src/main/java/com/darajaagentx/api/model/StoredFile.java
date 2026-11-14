package com.darajaagentx.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A file kept in the database, for servers that have no disk worth trusting (a free host that wipes its disk on every
 * deploy) and no file service. The id is the file's key, chosen by this service, for example
 * {@code profile-pictures/3f2c...e1.jpg}.
 */
@Document("stored_files")
@Entity
@Table(name = "stored_files")
public class StoredFile {

    @Id
    @Column(name = "file_key", length = 160, nullable = false, updatable = false)
    private String id;

    @Column(nullable = false)
    private byte[] data;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Empty means keep it until it is deleted. */
    private Instant expiresAt;

    protected StoredFile() {
        // for JPA
    }

    public StoredFile(String id, byte[] data, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.data = data;
        this.sizeBytes = data.length;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getId() {
        return id;
    }

    public byte[] getData() {
        return data;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean expired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }
}
