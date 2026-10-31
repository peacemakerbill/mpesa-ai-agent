package com.mpesaagent.companion.model;

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

/** A phone that has proved, with a code delivered to the number, that it belongs to this customer. */
@Document("devices")
@CompoundIndexes({
        @CompoundIndex(name = "uk_devices_phone_device", def = "{'phone': 1, 'deviceId': 1}", unique = true)
})
@Entity
@Table(name = "devices")
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 15)
    private String phone;

    /** Random id the app generates once and keeps. */
    @Column(nullable = false, length = 64)
    private String deviceId;

    @Column(length = 80)
    private String deviceName;

    @Column(nullable = false)
    private boolean revoked;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant lastSeenAt;

    /** Only used with MongoDB. Counts changes so two writers cannot silently overwrite each other. Not a database column in SQL. */
    @Version
    @Transient
    private Long mongoVersion;

    protected Device() {
        // for JPA
    }

    public Device(String phone, String deviceId, String deviceName) {
        this.phone = phone;
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        Instant now = Instant.now();
        this.createdAt = now;
        this.lastSeenAt = now;
    }

    /**
     * Called by the MongoDB store just before saving. MongoDB does not generate ids or run JPA lifecycle callbacks,
     * so the id and the timestamps are set here. With SQL databases this is never called.
     */
    public void prepareForMongo(Instant now) {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public UUID getId() {
        return id;
    }

    public String getPhone() {
        return phone;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
}
