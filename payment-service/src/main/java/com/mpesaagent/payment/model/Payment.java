package com.mpesaagent.payment.model;

import jakarta.persistence.Column;
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
import java.util.UUID;

@Document("payments")
@CompoundIndexes({
        @CompoundIndex(name = "phone_created", def = "{'phone': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "status_updated", def = "{'status': 1, 'updatedAt': 1}")
})
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Indexed(unique = true)
    @Column(nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    /** Customer phone in Daraja format, 2547XXXXXXXX or 2541XXXXXXXX. */
    @Column(nullable = false, length = 15)
    private String phone;

    /** Whole Kenya shillings. M-Pesa Express does not accept decimals. */
    @Column(nullable = false)
    private int amount;

    @Column(nullable = false, length = 12)
    private String accountReference;

    /** Stored as plain VARCHAR on every database, so schema validation also passes on H2 in tests. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(length = 60)
    private String merchantRequestId;

    @Indexed(unique = true, sparse = true)
    @Column(unique = true, length = 60)
    private String checkoutRequestId;

    @Column(length = 30)
    private String mpesaReceipt;

    /** Kept as text because Safaricom is not consistent about numeric versus alphanumeric codes. */
    @Column(length = 20)
    private String resultCode;

    @Column(length = 255)
    private String resultDesc;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** Only used with MongoDB. Counts changes so two writers cannot silently overwrite each other. Not a database column in SQL. */
    @Version
    @Transient
    private Long mongoVersion;

    protected Payment() {
        // for JPA
    }

    public Payment(String idempotencyKey, String phone, int amount, String accountReference) {
        this.idempotencyKey = idempotencyKey;
        this.phone = phone;
        this.amount = amount;
        this.accountReference = accountReference;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
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

    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getPhone() { return phone; }
    public int getAmount() { return amount; }
    public String getAccountReference() { return accountReference; }
    public PaymentStatus getStatus() { return status; }
    public String getMerchantRequestId() { return merchantRequestId; }
    public String getCheckoutRequestId() { return checkoutRequestId; }
    public String getMpesaReceipt() { return mpesaReceipt; }
    public String getResultCode() { return resultCode; }
    public String getResultDesc() { return resultDesc; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(PaymentStatus status) { this.status = status; }
    public void setMerchantRequestId(String merchantRequestId) { this.merchantRequestId = merchantRequestId; }
    public void setCheckoutRequestId(String checkoutRequestId) { this.checkoutRequestId = checkoutRequestId; }
    public void setMpesaReceipt(String mpesaReceipt) { this.mpesaReceipt = mpesaReceipt; }
    public void setResultCode(String resultCode) { this.resultCode = resultCode; }
    public void setResultDesc(String resultDesc) { this.resultDesc = resultDesc; }
}
