package com.darajaagentx.api.model;

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
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One thing the customer asked Daraja to do on a business account, from prepare to result.
 *
 * <p>No secrets are stored here. The account is named by id, and the unlocked credentials are read from the vault only
 * for the moment the call is made.
 */
@Document("business_operations")
@CompoundIndexes({
        @CompoundIndex(name = "uk_business_operations_key", def = "{'ownerPhone': 1, 'idempotencyKey': 1}", unique = true),
        @CompoundIndex(name = "idx_business_operations_owner", def = "{'ownerPhone': 1, 'createdAt': -1}")
})
@Entity
@Table(name = "business_operations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_business_operations_key", columnNames = {"owner_phone", "idempotency_key"})
})
public class BusinessOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 15)
    private String ownerPhone;

    @Column(nullable = false)
    private UUID accountId;

    /** The account's name when this was prepared, so the history still reads well if the account is renamed or removed. */
    @Column(nullable = false, length = 80)
    private String accountLabel;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private BusinessOperationType type;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private BusinessOperationStatus status = BusinessOperationStatus.PENDING_APPROVAL;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false, length = 300)
    private String summary;

    /** What the operation needs, for example phone, reference, receipt. Never a secret. */
    @Convert(converter = DetailsConverter.class)
    @Column(nullable = false, length = 2000)
    private Map<String, String> params = new LinkedHashMap<>();

    /** Where the request came from: TEXT, VOICE, IMAGE, DOCUMENT or CONTACT. */
    @Column(length = 12)
    private String source;

    @Column(nullable = false, length = 64)
    private String idempotencyKey;

    /** Part of the URLs Safaricom calls back. Without it nobody can post a result for this operation. */
    @Column(nullable = false, length = 40)
    private String callbackToken;

    @Column(length = 64)
    private String originatorConversationId;

    @Column(length = 64)
    private String conversationId;

    @Column(length = 64)
    private String checkoutRequestId;

    @Column(length = 30)
    private String receipt;

    @Column(length = 2000)
    private String resultText;

    @Column(length = 500)
    private String failure;

    @Column(length = 20)
    private String approvalMethod;

    @Column
    private Instant expiresAt;

    @Column
    private Instant approvedAt;

    @Column
    private Instant completedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** Only used with MongoDB. Not a database column in SQL. */
    @Version
    @Transient
    private Long mongoVersion;

    protected BusinessOperation() {
        // for JPA
    }

    public BusinessOperation(String ownerPhone, UUID accountId, String accountLabel, BusinessOperationType type) {
        this.ownerPhone = ownerPhone;
        this.accountId = accountId;
        this.accountLabel = accountLabel;
        this.type = type;
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
    public UUID getAccountId() { return accountId; }
    public String getAccountLabel() { return accountLabel; }
    public BusinessOperationType getType() { return type; }
    public BusinessOperationStatus getStatus() { return status; }
    public void setStatus(BusinessOperationStatus status) { this.status = status; }
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public Map<String, String> getParams() { return params; }
    public void setParams(Map<String, String> params) { this.params = params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params); }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getCallbackToken() { return callbackToken; }
    public void setCallbackToken(String callbackToken) { this.callbackToken = callbackToken; }
    public String getOriginatorConversationId() { return originatorConversationId; }
    public void setOriginatorConversationId(String v) { this.originatorConversationId = v; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String v) { this.conversationId = v; }
    public String getCheckoutRequestId() { return checkoutRequestId; }
    public void setCheckoutRequestId(String v) { this.checkoutRequestId = v; }
    public String getReceipt() { return receipt; }
    public void setReceipt(String receipt) { this.receipt = receipt; }
    public String getResultText() { return resultText; }
    public void setResultText(String resultText) { this.resultText = resultText; }
    public String getFailure() { return failure; }
    public void setFailure(String failure) { this.failure = failure; }
    public String getApprovalMethod() { return approvalMethod; }
    public void setApprovalMethod(String approvalMethod) { this.approvalMethod = approvalMethod; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
