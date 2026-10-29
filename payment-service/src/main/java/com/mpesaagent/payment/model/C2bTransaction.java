package com.mpesaagent.payment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.math.BigDecimal;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/** A customer paid our Paybill or Till directly (from the M-Pesa menu or app), reported by Safaricom. */
@Document("c2b_transactions")
@Entity
@Table(name = "c2b_transactions")
public class C2bTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Indexed(unique = true)
    @Column(nullable = false, unique = true, length = 30)
    private String transId;

    @Column(length = 30)
    private String transType;

    @Column(length = 20)
    private String transTime;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 20)
    private String shortcode;

    @Column(length = 50)
    private String billRefNumber;

    /** Safaricom sends a hashed MSISDN in production, so never rely on it to identify a customer. */
    @Column(length = 100)
    private String msisdn;

    @Column(length = 150)
    private String payerName;

    @Column(length = 30)
    private String orgBalance;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Only used with MongoDB. Counts changes so two writers cannot silently overwrite each other. Not a database column in SQL. */
    @Version
    @Transient
    private Long mongoVersion;

    protected C2bTransaction() {
        // for JPA
    }

    public C2bTransaction(String transId, String transType, String transTime, BigDecimal amount, String shortcode,
                          String billRefNumber, String msisdn, String payerName, String orgBalance) {
        this.transId = transId;
        this.transType = transType;
        this.transTime = transTime;
        this.amount = amount;
        this.shortcode = shortcode;
        this.billRefNumber = billRefNumber;
        this.msisdn = msisdn;
        this.payerName = payerName;
        this.orgBalance = orgBalance;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
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
    }

    public UUID getId() { return id; }
    public String getTransId() { return transId; }
    public String getTransType() { return transType; }
    public String getTransTime() { return transTime; }
    public BigDecimal getAmount() { return amount; }
    public String getShortcode() { return shortcode; }
    public String getBillRefNumber() { return billRefNumber; }
    public String getMsisdn() { return msisdn; }
    public String getPayerName() { return payerName; }
    public String getOrgBalance() { return orgBalance; }
    public Instant getCreatedAt() { return createdAt; }
}
