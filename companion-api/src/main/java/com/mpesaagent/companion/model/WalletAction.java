package com.mpesaagent.companion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Something the customer wants to do from their own M-Pesa. We never hold money or a PIN: this row only says
 * what to do, who it is for, and what the customer told us happened.
 */
@Document("wallet_actions")
@CompoundIndexes({
        @CompoundIndex(name = "phone_created", def = "{'phone': 1, 'createdAt': -1}")
})
@Entity
@Table(name = "wallet_actions")
public class WalletAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Indexed(unique = true)
    @Column(nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    /** The phone number of the customer (2547XXXXXXXX). Every read and write checks it. */
    @Column(nullable = false, length = 15)
    private String phone;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ActionType type;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ActionStatus status = ActionStatus.CREATED;

    /** Whole Kenya shillings. */
    @Column(nullable = false)
    private int amount;

    @Column(length = 80)
    private String label;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 12)
    private ActionSource source = ActionSource.TEXT;

    @Convert(converter = DetailsConverter.class)
    @Column(nullable = false, length = 2000)
    private Map<String, String> details = new LinkedHashMap<>();

    /** The M-Pesa code the customer sent us afterwards. Not verified by us. */
    @Column(length = 30)
    private String receipt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 12)
    private Verification verification = Verification.NONE;

    /** The code from an M-Pesa message on the customer's phone that was matched to this request. */
    @Indexed(unique = true, sparse = true)
    @Column(length = 12)
    private String smsCode;

    private Instant smsMatchedAt;

    @Column(length = 64)
    private String approvedDeviceId;

    private Instant approvedAt;

    private Instant reportedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** Only used with MongoDB. Counts changes so two writers cannot silently overwrite each other. Not a database column in SQL. */
    @Version
    @Transient
    private Long mongoVersion;

    protected WalletAction() {
        // for JPA
    }

    public WalletAction(String idempotencyKey, String phone, ActionType type, int amount, String label,
                        ActionSource source, Map<String, String> details, Instant expiresAt) {
        this.idempotencyKey = idempotencyKey;
        this.phone = phone;
        this.type = type;
        this.amount = amount;
        this.label = label;
        this.source = source;
        this.details = new LinkedHashMap<>(details);
        this.expiresAt = expiresAt;
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

    /**
     * Called by the MongoDB store just before saving. MongoDB does not generate ids or run JPA lifecycle callbacks,
     * so the id and the timestamps are set here. With SQL databases this is never called.
     */
    public void prepareForMongo(Instant now) {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getPhone() {
        return phone;
    }

    public ActionType getType() {
        return type;
    }

    public ActionStatus getStatus() {
        return status;
    }

    public void setStatus(ActionStatus status) {
        this.status = status;
    }

    public int getAmount() {
        return amount;
    }

    public String getLabel() {
        return label;
    }

    public ActionSource getSource() {
        return source;
    }

    public Map<String, String> getDetails() {
        return details;
    }

    public String getReceipt() {
        return receipt;
    }

    public void setReceipt(String receipt) {
        this.receipt = receipt;
    }

    public Verification getVerification() {
        return verification;
    }

    /** A level can only go up. */
    public void raiseVerification(Verification level) {
        if (level.compareTo(verification) > 0) {
            this.verification = level;
        }
    }

    public String getSmsCode() {
        return smsCode;
    }

    public Instant getSmsMatchedAt() {
        return smsMatchedAt;
    }

    public void recordSms(String code, Instant at) {
        this.smsCode = code;
        this.smsMatchedAt = at;
    }

    public String getApprovedDeviceId() {
        return approvedDeviceId;
    }

    public void setApprovedDeviceId(String approvedDeviceId) {
        this.approvedDeviceId = approvedDeviceId;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public Instant getReportedAt() {
        return reportedAt;
    }

    public void setReportedAt(Instant reportedAt) {
        this.reportedAt = reportedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
