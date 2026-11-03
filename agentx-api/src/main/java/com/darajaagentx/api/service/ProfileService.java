package com.darajaagentx.api.service;

import com.darajaagentx.api.config.AgentXProperties;
import com.darajaagentx.api.config.MailProperties;
import com.darajaagentx.api.config.StorageProperties;
import com.darajaagentx.api.dto.CodeRequestResponse;
import com.darajaagentx.api.dto.ProfileUpdate;
import com.darajaagentx.api.dto.ProfileView;
import com.darajaagentx.api.exception.InvalidActionRequestException;
import com.darajaagentx.api.exception.TooManyRequestsException;
import com.darajaagentx.api.mail.MailDeliveryException;
import com.darajaagentx.api.model.ProfileStatus;
import com.darajaagentx.api.model.UserProfile;
import com.darajaagentx.api.repository.UserProfileRepository;
import com.darajaagentx.api.storage.FileStore;
import com.darajaagentx.api.storage.PictureProcessor;
import com.darajaagentx.api.util.Emails;
import com.darajaagentx.api.util.Hmac;
import com.darajaagentx.api.util.PersonNames;
import com.darajaagentx.api.util.Phones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Reading and changing a customer's profile: name, address, location, picture, the email notices they want, and the email
 * address itself. An address becomes the customer's only after the 6 digit code sent to it is typed back, and a new
 * address waits beside the old one until then, so a mistyped address never replaces a working one.
 *
 * <p>People who signed in before profiles existed get an empty profile the first time they ask for it, so nothing
 * breaks for them.
 */
@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration RESEND_GAP = Duration.ofSeconds(30);

    /** A stored picture with the type to serve it as and a number that changes when it does. */
    public record Picture(byte[] data, String type, long version) {
    }

    private final UserProfileRepository profiles;
    private final FileStore files;
    private final int maxPictureBytes;
    private final MailProperties mail;
    private final CustomerMailer mailer;
    private final SlidingWindowLimiter limiter;
    private final String secret;
    private final boolean exposeCodes;
    private final boolean emailDelivers;
    private final Clock clock;

    @Autowired
    public ProfileService(UserProfileRepository profiles, FileStore files, StorageProperties storage, MailProperties mail,
                          CustomerMailer mailer, MailOutbox outbox, SlidingWindowLimiter limiter, AgentXProperties agentx,
                          CodeDelivery delivery, Clock clock) {
        this(profiles, files, storage.maxPictureBytes(), mail, mailer, limiter, agentx.tokenSecret(),
                delivery.mode() == CodeDelivery.Mode.DEV, outbox.delivers(), clock);
    }

    ProfileService(UserProfileRepository profiles, FileStore files, int maxPictureBytes, MailProperties mail, CustomerMailer mailer,
                   SlidingWindowLimiter limiter, String secret, boolean exposeCodes, boolean emailDelivers, Clock clock) {
        this.profiles = profiles;
        this.files = files;
        this.maxPictureBytes = maxPictureBytes;
        this.mail = mail;
        this.mailer = mailer;
        this.limiter = limiter;
        this.secret = secret;
        this.exposeCodes = exposeCodes;
        this.emailDelivers = emailDelivers;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ reading

    /** The profile of a signed in customer, made on first use. Being signed in proves the phone, so a half finished sign up becomes active. */
    @Transactional
    public UserProfile getOrCreate(String rawPhone) {
        String phone = Phones.normalize(rawPhone);
        Optional<UserProfile> found = profiles.findByPhone(phone);
        if (found.isPresent()) {
            UserProfile p = found.get();
            if (p.getStatus() != ProfileStatus.ACTIVE) {
                p.setStatus(ProfileStatus.ACTIVE);
                p.setSignupExpiresAt(null);
                return profiles.save(p);
            }
            return p;
        }
        return profiles.save(new UserProfile(phone));
    }

    @Transactional
    public ProfileView view(String phone) {
        return view(getOrCreate(phone));
    }

    public ProfileView view(UserProfile p) {
        return ProfileView.of(p, emailDelivers);
    }

    /** The address reports go to when the customer says "my email", or empty when none is verified. */
    @Transactional
    public Optional<String> verifiedEmail(String phone) {
        UserProfile p = getOrCreate(phone);
        return p.isEmailVerified() && p.getEmail() != null ? Optional.of(p.getEmail()) : Optional.empty();
    }

    // ------------------------------------------------------------------ details

    @Transactional
    public ProfileView update(String phone, ProfileUpdate u) {
        UserProfile p = getOrCreate(phone);
        if (u == null) {
            return view(p);
        }
        if (u.firstName() != null) {
            p.setFirstName(PersonNames.required(u.firstName(), "first name"));
        }
        if (u.lastName() != null) {
            p.setLastName(PersonNames.required(u.lastName(), "last name"));
        }
        if (u.middleName() != null) {
            p.setMiddleName(PersonNames.clean(u.middleName(), "middle name"));
        }
        if (u.address() != null) {
            p.setAddress(text(u.address(), 200, "address"));
        }
        if (u.city() != null) {
            p.setCity(text(u.city(), 80, "town or city"));
        }
        if (u.county() != null) {
            p.setCounty(text(u.county(), 80, "county"));
        }
        if (u.country() != null) {
            p.setCountry(text(u.country(), 80, "country"));
        }
        if (Boolean.TRUE.equals(u.clearLocation())) {
            p.setLatitude(null);
            p.setLongitude(null);
        } else if (u.latitude() != null || u.longitude() != null) {
            if (u.latitude() == null || u.longitude() == null) {
                throw new InvalidActionRequestException("Give both the latitude and the longitude of your location.");
            }
            if (!(u.latitude() >= -90 && u.latitude() <= 90) || !(u.longitude() >= -180 && u.longitude() <= 180)) {
                throw new InvalidActionRequestException("That location is not on the map. Latitude is -90 to 90, longitude -180 to 180.");
            }
            p.setLatitude(Math.round(u.latitude() * 1_000_000d) / 1_000_000d);
            p.setLongitude(Math.round(u.longitude() * 1_000_000d) / 1_000_000d);
        }
        if (u.notifySignIn() != null) {
            p.setNotifySignIn(u.notifySignIn());
        }
        if (u.notifyPayments() != null) {
            p.setNotifyPayments(u.notifyPayments());
        }
        return view(profiles.save(p));
    }

    // ------------------------------------------------------------------ email

    /**
     * Sends a code to [rawEmail]. Until it is typed back the address only waits. The answer carries the code itself
     * only when this server runs in dev mode with the log mail transport, so a test console can finish without an inbox.
     */
    @Transactional
    public CodeRequestResponse requestEmailCode(String phone, String rawEmail) {
        UserProfile p = getOrCreate(phone);
        String email = Emails.require(rawEmail);
        if (p.isEmailVerified() && email.equals(p.getEmail())) {
            throw new InvalidActionRequestException("That email address is already verified.");
        }
        Instant now = clock.instant();
        if (p.getEmailCodeSentAt() != null && now.isBefore(p.getEmailCodeSentAt().plus(RESEND_GAP))) {
            throw new TooManyRequestsException("A code was just sent. Wait half a minute before asking again.");
        }
        if (!limiter.allow("email-code:" + p.getPhone(), Math.max(1, mail.codesPerHour()), HOUR)) {
            throw new TooManyRequestsException("Too many codes were requested. Please try again in an hour.");
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        Instant expires = now.plus(mail.codeTtl());
        UserProfile before = copyCodeState(p);
        p.setPendingEmail(email);
        p.setEmailCodeHash(hash(p.getPhone(), email, code));
        p.setEmailCodeExpiresAt(expires);
        p.setEmailCodeAttempts(0);
        p.setEmailCodeSentAt(now);
        profiles.save(p);
        try {
            mailer.emailCode(p, email, code);
        } catch (MailDeliveryException e) {
            restoreCodeState(p, before);
            profiles.save(p);
            throw e;
        }
        return new CodeRequestResponse(expires, exposeCodes && !emailDelivers ? code : null);
    }

    /** Wrong tries must be remembered even though the request fails. */
    @Transactional(noRollbackFor = InvalidActionRequestException.class)
    public ProfileView verifyEmail(String phone, String code) {
        UserProfile p = getOrCreate(phone);
        Instant now = clock.instant();
        String bad = "That code is not valid or has expired. Ask for a new one.";
        if (p.getPendingEmail() == null || p.getEmailCodeHash() == null || p.getEmailCodeExpiresAt() == null || !now.isBefore(p.getEmailCodeExpiresAt())) {
            throw new InvalidActionRequestException(bad);
        }
        if (p.getEmailCodeAttempts() >= mail.codeMaxAttempts()) {
            p.clearEmailCode();
            profiles.save(p);
            throw new InvalidActionRequestException(bad);
        }
        p.setEmailCodeAttempts(p.getEmailCodeAttempts() + 1);
        boolean match = code != null && MessageDigest.isEqual(p.getEmailCodeHash().getBytes(StandardCharsets.UTF_8),
                hash(p.getPhone(), p.getPendingEmail(), code).getBytes(StandardCharsets.UTF_8));
        if (!match) {
            if (p.getEmailCodeAttempts() >= mail.codeMaxAttempts()) {
                p.clearEmailCode();
            }
            profiles.save(p);
            throw new InvalidActionRequestException(bad);
        }
        boolean first = p.getEmailVerifiedAt() == null;
        p.setEmail(p.getPendingEmail());
        p.setEmailVerified(true);
        p.setEmailVerifiedAt(now);
        p.clearEmailCode();
        UserProfile saved = profiles.save(p);
        if (first) {
            mailer.welcome(saved);
        }
        return view(saved);
    }

    /** Removes the email address (and any address waiting for its code). Notices and reports to "my email" stop. */
    @Transactional
    public ProfileView removeEmail(String phone) {
        UserProfile p = getOrCreate(phone);
        p.setEmail(null);
        p.setEmailVerified(false);
        p.setEmailVerifiedAt(null);
        p.clearEmailCode();
        return view(profiles.save(p));
    }

    // ------------------------------------------------------------------ picture

    @Transactional
    public ProfileView setPicture(String phone, byte[] raw) {
        if (raw == null || raw.length == 0) {
            throw new InvalidActionRequestException("The picture is empty.");
        }
        if (raw.length > maxPictureBytes) {
            throw new InvalidActionRequestException("That picture is too large. Use one under " + (maxPictureBytes / 1_048_576.0 >= 1
                    ? String.format("%.0f MB", maxPictureBytes / 1_048_576.0) : (maxPictureBytes / 1024) + " KB") + ".");
        }
        if (!limiter.allow("picture:" + Phones.normalize(phone), 20, HOUR)) {
            throw new TooManyRequestsException("Too many picture changes. Please try again later.");
        }
        UserProfile p = getOrCreate(phone);
        PictureProcessor.Result picture = PictureProcessor.process(raw);
        Instant now = clock.instant();
        String key = "profile-pictures/" + p.getId() + "-" + now.toEpochMilli() + "." + picture.extension();
        String oldKey = p.getPictureKey();
        files.put(key, picture.data());
        p.setPictureKey(key);
        p.setPictureType(picture.type());
        p.setPictureUpdatedAt(now);
        UserProfile saved;
        try {
            saved = profiles.save(p);
        } catch (RuntimeException e) {
            deleteQuietly(key);
            throw e;
        }
        if (oldKey != null && !oldKey.equals(key)) {
            deleteQuietly(oldKey);
        }
        return view(saved);
    }

    public Optional<Picture> picture(String phone) {
        UserProfile p = getOrCreate(phone);
        if (!p.hasPicture()) {
            return Optional.empty();
        }
        return files.get(p.getPictureKey()).map(bytes -> new Picture(bytes, p.getPictureType() == null ? "image/jpeg" : p.getPictureType(),
                p.getPictureUpdatedAt() == null ? 0 : p.getPictureUpdatedAt().toEpochMilli()));
    }

    @Transactional
    public ProfileView removePicture(String phone) {
        UserProfile p = getOrCreate(phone);
        String old = p.getPictureKey();
        p.setPictureKey(null);
        p.setPictureType(null);
        p.setPictureUpdatedAt(null);
        UserProfile saved = profiles.save(p);
        if (old != null) {
            deleteQuietly(old);
        }
        return view(saved);
    }

    // ------------------------------------------------------------------ helpers

    private void deleteQuietly(String key) {
        try {
            files.delete(key);
        } catch (RuntimeException e) {
            log.warn("Could not remove the file {}: {}", key, e.getMessage());
        }
    }

    private String hash(String phone, String email, String code) {
        return Hmac.hex("email-code:" + secret, phone + ":" + email + ":" + code);
    }

    /** Cleans a free text field: single spaces, no control characters, a length limit. Empty clears it. */
    static String text(String raw, int max, String what) {
        String s = raw.trim().replaceAll("\\s+", " ");
        if (s.isEmpty()) {
            return null;
        }
        if (s.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidActionRequestException("The " + what + " has characters that are not allowed.");
        }
        if (s.length() > max) {
            throw new InvalidActionRequestException("The " + what + " is too long. Use " + max + " characters or fewer.");
        }
        return s;
    }

    private static UserProfile copyCodeState(UserProfile p) {
        UserProfile c = new UserProfile(p.getPhone());
        c.setPendingEmail(p.getPendingEmail());
        c.setEmailCodeHash(p.getEmailCodeHash());
        c.setEmailCodeExpiresAt(p.getEmailCodeExpiresAt());
        c.setEmailCodeAttempts(p.getEmailCodeAttempts());
        c.setEmailCodeSentAt(p.getEmailCodeSentAt());
        return c;
    }

    private static void restoreCodeState(UserProfile p, UserProfile before) {
        p.setPendingEmail(before.getPendingEmail());
        p.setEmailCodeHash(before.getEmailCodeHash());
        p.setEmailCodeExpiresAt(before.getEmailCodeExpiresAt());
        p.setEmailCodeAttempts(before.getEmailCodeAttempts());
        p.setEmailCodeSentAt(before.getEmailCodeSentAt());
    }
}
