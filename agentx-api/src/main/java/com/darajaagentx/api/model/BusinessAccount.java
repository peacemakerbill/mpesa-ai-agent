package com.darajaagentx.api.model;

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
import jakarta.persistence.UniqueConstraint;

import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/**
 * One M-Pesa shortcode a customer runs a business on, with the Daraja credentials for it.
 *
 * <p>The consumer key, consumer secret, initiator password and passkey are stored encrypted (see SecretVault) and are
 * never sent back to the phone. A child (a till, a branch code or a store number under a head office) has a parent.
 * A child either shares the head office's shortcode and credentials, or has its own shortcode (a store number that
 * went live on its own) and then its own credentials, any group of which it can leave empty to use the head office's.
 */
@Document("business_accounts")
@CompoundIndexes({
        @CompoundIndex(name = "uk_business_accounts_code",
                def = "{'ownerPhone': 1, 'environment': 1, 'shortcode': 1, 'tillNumber': 1, 'accountReference': 1}", unique = true),
        @CompoundIndex(name = "idx_business_accounts_parent", def = "{'ownerPhone': 1, 'parentId': 1}")
})
@Entity
@Table(name = "business_accounts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_business_accounts_code",
                columnNames = {"owner_phone", "environment", "shortcode", "till_number", "account_reference"})
})
public class BusinessAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The signed-in customer who owns this account. */
    @Column(nullable = false, length = 15)
    private String ownerPhone;

    @Column(nullable = false, length = 80)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private DarajaEnvironment environment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ShortcodeType shortcodeType;

    /** The shortcode this account transacts on: the Paybill number, or the Buy Goods store (head office) number. A child shares its head's, or has its own. */
    @Column(nullable = false, length = 12)
    private String shortcode;

    /** The Buy Goods till number (the receiving party). Empty when there is none, so the unique key behaves the same in every database. */
    @Column(nullable = false, length = 12)
    private String tillNumber = "";

    /** The Paybill account number a branch collects under. Empty when there is none. */
    @Column(nullable = false, length = 12)
    private String accountReference = "";

    /** Set on a child (a till, branch code or store number under a head office). */
    @Column
    private UUID parentId;

    /** The last four characters of the consumer key, so the person can recognise which key is saved. */
    @Column(length = 8)
    private String consumerKeyHint;

    @Column(length = 500)
    private String consumerKeyEnc;

    @Column(length = 500)
    private String consumerSecretEnc;

    @Column(length = 50)
    private String initiatorName;

    @Column(length = 500)
    private String initiatorPasswordEnc;

    @Column(length = 700)
    private String passkeyEnc;

    /** When Safaricom last accepted the consumer key and secret. */
    @Column
    private Instant verifiedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** Only used with MongoDB. Counts changes so two writers cannot silently overwrite each other. Not a database column in SQL. */
    @Version
    @Transient
    private Long mongoVersion;

    protected BusinessAccount() {
        // for JPA
    }

    public BusinessAccount(String ownerPhone) {
        this.ownerPhone = ownerPhone;
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

    /** Called by the MongoDB store just before saving, because MongoDB makes no ids or timestamps. Never called with SQL. */
    public void prepareForMongo(Instant now) {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    public boolean isBranch() {
        return parentId != null;
    }

    /** True when this account itself holds any credential. A child with none uses the head office's. */
    public boolean holdsOwnCredentials() {
        return consumerKeyEnc != null || consumerSecretEnc != null || passkeyEnc != null || initiatorPasswordEnc != null
                || (initiatorName != null && !initiatorName.isBlank());
    }

    /** Removes every credential saved on this account. */
    public void clearCredentials() {
        consumerKeyEnc = null;
        consumerKeyHint = null;
        consumerSecretEnc = null;
        initiatorName = null;
        initiatorPasswordEnc = null;
        passkeyEnc = null;
        verifiedAt = null;
    }

    public UUID getId() {
        return id;
    }

    public String getOwnerPhone() {
        return ownerPhone;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public DarajaEnvironment getEnvironment() {
        return environment;
    }

    public void setEnvironment(DarajaEnvironment environment) {
        this.environment = environment;
    }

    public ShortcodeType getShortcodeType() {
        return shortcodeType;
    }

    public void setShortcodeType(ShortcodeType shortcodeType) {
        this.shortcodeType = shortcodeType;
    }

    public String getShortcode() {
        return shortcode;
    }

    public void setShortcode(String shortcode) {
        this.shortcode = shortcode;
    }

    public String getTillNumber() {
        return tillNumber;
    }

    public void setTillNumber(String tillNumber) {
        this.tillNumber = tillNumber == null ? "" : tillNumber;
    }

    public String getAccountReference() {
        return accountReference;
    }

    public void setAccountReference(String accountReference) {
        this.accountReference = accountReference == null ? "" : accountReference;
    }

    public UUID getParentId() {
        return parentId;
    }

    public void setParentId(UUID parentId) {
        this.parentId = parentId;
    }

    public String getConsumerKeyHint() {
        return consumerKeyHint;
    }

    public void setConsumerKeyHint(String consumerKeyHint) {
        this.consumerKeyHint = consumerKeyHint;
    }

    public String getConsumerKeyEnc() {
        return consumerKeyEnc;
    }

    public void setConsumerKeyEnc(String consumerKeyEnc) {
        this.consumerKeyEnc = consumerKeyEnc;
    }

    public String getConsumerSecretEnc() {
        return consumerSecretEnc;
    }

    public void setConsumerSecretEnc(String consumerSecretEnc) {
        this.consumerSecretEnc = consumerSecretEnc;
    }

    public String getInitiatorName() {
        return initiatorName;
    }

    public void setInitiatorName(String initiatorName) {
        this.initiatorName = initiatorName;
    }

    public String getInitiatorPasswordEnc() {
        return initiatorPasswordEnc;
    }

    public void setInitiatorPasswordEnc(String initiatorPasswordEnc) {
        this.initiatorPasswordEnc = initiatorPasswordEnc;
    }

    public String getPasskeyEnc() {
        return passkeyEnc;
    }

    public void setPasskeyEnc(String passkeyEnc) {
        this.passkeyEnc = passkeyEnc;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean hasConsumerCredentials() {
        return consumerKeyEnc != null && consumerSecretEnc != null;
    }

    public boolean hasInitiator() {
        return initiatorName != null && !initiatorName.isBlank() && initiatorPasswordEnc != null;
    }

    public boolean hasPasskey() {
        return passkeyEnc != null;
    }
}
