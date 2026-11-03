package com.darajaagentx.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;

import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/** An address a customer keeps for sending reports to, for example their accountant. Added one by one or uploaded as a list. */
@Document("email_recipients")
@CompoundIndexes({
        @CompoundIndex(name = "uk_email_recipients_owner_email", def = "{'ownerPhone': 1, 'email': 1}", unique = true)
})
@Entity
@Table(name = "email_recipients", uniqueConstraints = {
        @UniqueConstraint(name = "uk_email_recipients_owner_email", columnNames = {"owner_phone", "email"})
})
public class EmailRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 15)
    private String ownerPhone;

    @Column(nullable = false, length = 254)
    private String email;

    /** What the customer calls this person, for example "Accountant". The chat can say "send it to my accountant". */
    @Column(length = 80)
    private String name;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** Only used with MongoDB. Not a database column in SQL. */
    @Version
    @Transient
    private Long mongoVersion;

    protected EmailRecipient() {
        // for JPA
    }

    public EmailRecipient(String ownerPhone, String email, String name) {
        this.ownerPhone = ownerPhone;
        this.email = email;
        this.name = name;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Called by the MongoDB store just before saving. Never called with SQL. */
    public void prepareForMongo(Instant now) {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getOwnerPhone() { return ownerPhone; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
