package com.mganji.payment.model;

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

@Document("mpesa_operations")
@CompoundIndexes({
        @CompoundIndex(name = "status_created", def = "{'status': 1, 'createdAt': 1}"),
        @CompoundIndex(name = "requested_by_created", def = "{'requestedBy': 1, 'createdAt': -1}")
})
@Entity
@Table(name = "mpesa_operations")
public class MpesaOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Indexed(unique = true)
    @Column(nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private OperationType type;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private OperationStatus status;

    /** Phone (Daraja format) of whoever asked for this, customer or staff. */
    @Column(nullable = false, length = 15)
    private String requestedBy;

    @Column(length = 15)
    private String approvedBy;

    /** Who receives the money for B2C operations. */
    @Column(length = 15)
    private String recipientPhone;

    /** Whole KES, null for lookups. */
    private Integer amount;

    /** Receipt number of the transaction to look up or reverse. */
    @Column(length = 30)
    private String targetReceipt;

    /** The customer payment a refund is for. */
    private UUID relatedPaymentId;

    @Column(length = 255)
    private String reason;

    @Indexed(unique = true, sparse = true)
    @Column(unique = true, length = 60)
    private String originatorConversationId;

    @Column(length = 60)
    private String conversationId;

    @Column(length = 30)
    private String mpesaReceipt;

    @Column(length = 20)
    private String resultCode;

    @Column(length = 255)
    private String resultDesc;

    /** Result parameters from Safaricom as a small JSON object. */
    @Column(length = 4000)
    private String resultData;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** Only used with MongoDB. Counts changes so two writers cannot silently overwrite each other. Not a database column in SQL. */
    @Version
    @Transient
    private Long mongoVersion;

    protected MpesaOperation() {
        // for JPA
    }

    public MpesaOperation(String idempotencyKey, OperationType type, String requestedBy) {
        this.idempotencyKey = idempotencyKey;
        this.type = type;
        this.requestedBy = requestedBy;
        this.status = type.requiresApproval() ? OperationStatus.PENDING_APPROVAL : OperationStatus.CREATED;
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
    public OperationType getType() { return type; }
    public OperationStatus getStatus() { return status; }
    public String getRequestedBy() { return requestedBy; }
    public String getApprovedBy() { return approvedBy; }
    public String getRecipientPhone() { return recipientPhone; }
    public Integer getAmount() { return amount; }
    public String getTargetReceipt() { return targetReceipt; }
    public UUID getRelatedPaymentId() { return relatedPaymentId; }
    public String getReason() { return reason; }
    public String getOriginatorConversationId() { return originatorConversationId; }
    public String getConversationId() { return conversationId; }
    public String getMpesaReceipt() { return mpesaReceipt; }
    public String getResultCode() { return resultCode; }
    public String getResultDesc() { return resultDesc; }
    public String getResultData() { return resultData; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(OperationStatus status) { this.status = status; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public void setRecipientPhone(String recipientPhone) { this.recipientPhone = recipientPhone; }
    public void setAmount(Integer amount) { this.amount = amount; }
    public void setTargetReceipt(String targetReceipt) { this.targetReceipt = targetReceipt; }
    public void setRelatedPaymentId(UUID relatedPaymentId) { this.relatedPaymentId = relatedPaymentId; }
    public void setReason(String reason) { this.reason = reason; }
    public void setOriginatorConversationId(String originatorConversationId) { this.originatorConversationId = originatorConversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public void setMpesaReceipt(String mpesaReceipt) { this.mpesaReceipt = mpesaReceipt; }
    public void setResultCode(String resultCode) { this.resultCode = resultCode; }
    public void setResultDesc(String resultDesc) { this.resultDesc = resultDesc; }
    public void setResultData(String resultData) { this.resultData = resultData; }
}
