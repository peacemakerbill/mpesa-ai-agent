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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/**
 * Who a customer is: name, email, address, location, picture and the email notices they want. One row per phone number.
 * The phone number is the account, it is proven by an SMS code, and it can not be changed here.
 *
 * <p>The email is separate from the phone: it counts as theirs only after a code sent to it was typed back
 * ({@link #isEmailVerified()}). A new address waits in {@code pendingEmail} with a hashed code until then, so a wrong
 * or someone else's address never replaces a good one.
 */
@Document("user_profiles")
@CompoundIndexes({
        @CompoundIndex(name = "uk_user_profiles_phone", def = "{'phone': 1}", unique = true)
})
@Entity
@Table(name = "user_profiles", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_profiles_phone", columnNames = {"phone"})
})
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 15)
    private String phone;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private ProfileStatus status = ProfileStatus.ACTIVE;

    @Column(length = 60)
    private String firstName;

    @Column(length = 60)
    private String middleName;

    @Column(length = 60)
    private String lastName;

    /** The verified address, the one reports and notices go to. */
    @Column(length = 254)
    private String email;

    @Column(nullable = false)
    private boolean emailVerified;

    @Column
    private Instant emailVerifiedAt;

    /** An address waiting for its code. */
    @Column(length = 254)
    private String pendingEmail;

    @Column(length = 64)
    private String emailCodeHash;

    @Column
    private Instant emailCodeExpiresAt;

    @Column(nullable = false)
    private int emailCodeAttempts;

    @Column
    private Instant emailCodeSentAt;

    @Column(length = 200)
    private String address;

    @Column(length = 80)
    private String city;

    @Column(length = 80)
    private String county;

    @Column(length = 80)
    private String country;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    /** The picture in the file store, for example profile-pictures/3f2c.jpg. Never a path on disk. */
    @Column(length = 120)
    private String pictureKey;

    @Column(length = 40)
    private String pictureType;

    @Column
    private Instant pictureUpdatedAt;

    /** Email me when a new sign in happens. */
    @Column(nullable = false)
    private boolean notifySignIn = true;

    /** Email me when a payment or business request finishes. */
    @Column(nullable = false)
    private boolean notifyPayments = true;

    @Column
    private Instant lastSignInMailAt;

    /** When a started sign up stops waiting for its code. */
    @Column
    private Instant signupExpiresAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** Only used with MongoDB. Not a database column in SQL. */
    @Version
    @Transient
    private Long mongoVersion;

    protected UserProfile() {
        // for JPA
    }

    public UserProfile(String phone) {
        this.phone = phone;
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

    public boolean hasPicture() {
        return pictureKey != null && !pictureKey.isBlank();
    }

    /** Puts a started email check away: the code, its tries and the address it was for. */
    public void clearEmailCode() {
        pendingEmail = null;
        emailCodeHash = null;
        emailCodeExpiresAt = null;
        emailCodeAttempts = 0;
    }

    public UUID getId() { return id; }
    public String getPhone() { return phone; }
    public ProfileStatus getStatus() { return status; }
    public void setStatus(ProfileStatus status) { this.status = status; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getMiddleName() { return middleName; }
    public void setMiddleName(String middleName) { this.middleName = middleName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
    public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
    public void setEmailVerifiedAt(Instant emailVerifiedAt) { this.emailVerifiedAt = emailVerifiedAt; }
    public String getPendingEmail() { return pendingEmail; }
    public void setPendingEmail(String pendingEmail) { this.pendingEmail = pendingEmail; }
    public String getEmailCodeHash() { return emailCodeHash; }
    public void setEmailCodeHash(String emailCodeHash) { this.emailCodeHash = emailCodeHash; }
    public Instant getEmailCodeExpiresAt() { return emailCodeExpiresAt; }
    public void setEmailCodeExpiresAt(Instant emailCodeExpiresAt) { this.emailCodeExpiresAt = emailCodeExpiresAt; }
    public int getEmailCodeAttempts() { return emailCodeAttempts; }
    public void setEmailCodeAttempts(int emailCodeAttempts) { this.emailCodeAttempts = emailCodeAttempts; }
    public Instant getEmailCodeSentAt() { return emailCodeSentAt; }
    public void setEmailCodeSentAt(Instant emailCodeSentAt) { this.emailCodeSentAt = emailCodeSentAt; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getCounty() { return county; }
    public void setCounty(String county) { this.county = county; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public String getPictureKey() { return pictureKey; }
    public void setPictureKey(String pictureKey) { this.pictureKey = pictureKey; }
    public String getPictureType() { return pictureType; }
    public void setPictureType(String pictureType) { this.pictureType = pictureType; }
    public Instant getPictureUpdatedAt() { return pictureUpdatedAt; }
    public void setPictureUpdatedAt(Instant pictureUpdatedAt) { this.pictureUpdatedAt = pictureUpdatedAt; }
    public boolean isNotifySignIn() { return notifySignIn; }
    public void setNotifySignIn(boolean notifySignIn) { this.notifySignIn = notifySignIn; }
    public boolean isNotifyPayments() { return notifyPayments; }
    public void setNotifyPayments(boolean notifyPayments) { this.notifyPayments = notifyPayments; }
    public Instant getLastSignInMailAt() { return lastSignInMailAt; }
    public void setLastSignInMailAt(Instant lastSignInMailAt) { this.lastSignInMailAt = lastSignInMailAt; }
    public Instant getSignupExpiresAt() { return signupExpiresAt; }
    public void setSignupExpiresAt(Instant signupExpiresAt) { this.signupExpiresAt = signupExpiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
