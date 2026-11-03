package com.darajaagentx.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/**
 * One line per email this service tried to send for a customer. It says what, to whom and whether it worked, and keeps
 * no body and no attachment. It answers "did my report go out?" and shows where a report went.
 */
@Document("email_log")
@CompoundIndexes({
        @CompoundIndex(name = "idx_email_log_owner", def = "{'ownerPhone': 1, 'createdAt': -1}")
})
@Entity
@Table(name = "email_log")
public class EmailLog {

    public static final String SENT = "SENT";
    public static final String FAILED = "FAILED";
    /** The server is set to the log transport: the email was written to the log and went nowhere. */
    public static final String LOGGED = "LOGGED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 15)
    private String ownerPhone;

    /** WELCOME, SIGN_IN, PAYMENT, EMAIL_CODE or REPORT. */
    @Column(nullable = false, length = 16)
    private String kind;

    /** The addresses, comma separated and cut to fit. */
    @Column(nullable = false, length = 500)
    private String recipients;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, length = 8)
    private String status;

    @Column(length = 300)
    private String error;

    /** The attached file name for a report, for example transactions.pdf. */
    @Column(length = 120)
    private String attachment;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Only used with MongoDB. Not a database column in SQL. */
    @Version
    @Transient
    private Long mongoVersion;

    protected EmailLog() {
        // for JPA
    }

    public EmailLog(String ownerPhone, String kind, String recipients, String subject, String status, String error, String attachment) {
        this.ownerPhone = ownerPhone;
        this.kind = kind;
        this.recipients = cut(recipients, 500);
        this.subject = cut(subject, 200);
        this.status = status;
        this.error = cut(error, 300);
        this.attachment = cut(attachment, 120);
    }

    private static String cut(String s, int max) {
        return s == null ? null : (s.length() <= max ? s : s.substring(0, max));
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    /** Called by the MongoDB store just before saving. Never called with SQL. */
    public void prepareForMongo(Instant now) {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
    }

    public UUID getId() { return id; }
    public String getOwnerPhone() { return ownerPhone; }
    public String getKind() { return kind; }
    public String getRecipients() { return recipients; }
    public String getSubject() { return subject; }
    public String getStatus() { return status; }
    public String getError() { return error; }
    public String getAttachment() { return attachment; }
    public Instant getCreatedAt() { return createdAt; }
}
