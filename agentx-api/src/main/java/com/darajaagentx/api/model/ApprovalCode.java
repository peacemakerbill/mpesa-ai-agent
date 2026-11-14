package com.darajaagentx.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/**
 * A short code sent by text message to approve one request. It works for that request only, for a few minutes,
 * and allows a few wrong tries. Only a keyed hash of it is stored.
 */
@Document("approval_codes")
@CompoundIndexes({
        @CompoundIndex(name = "phone_target_created", def = "{'phone': 1, 'targetId': 1, 'createdAt': -1}")
})
@Entity
@Table(name = "approval_codes")
public class ApprovalCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 15)
    private String phone;

    /** ACTION for a personal request, BUSINESS for a request on a business account. */
    @Column(nullable = false, length = 10)
    private String kind;

    @Column(nullable = false)
    private UUID targetId;

    @Column(nullable = false, length = 64)
    private String codeHash;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant usedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Only used with MongoDB. Counts changes so two writers cannot silently overwrite each other. Not a database column in SQL. */
    @Version
    @Transient
    private Long mongoVersion;

    protected ApprovalCode() {
        // for JPA
    }

    public ApprovalCode(String phone, String kind, UUID targetId, String codeHash, Instant expiresAt, Instant now) {
        this.phone = phone;
        this.kind = kind;
        this.targetId = targetId;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.createdAt = now;
    }

    /** Called by the MongoDB store just before saving, because MongoDB does not make ids. With SQL databases this is never called. */
    public void prepareForMongo(Instant now) {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public UUID getId() { return id; }
    public String getPhone() { return phone; }
    public String getKind() { return kind; }
    public UUID getTargetId() { return targetId; }
    public String getCodeHash() { return codeHash; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public void setUsedAt(Instant usedAt) { this.usedAt = usedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
