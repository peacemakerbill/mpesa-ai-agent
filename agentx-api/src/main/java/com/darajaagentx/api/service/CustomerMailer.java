package com.darajaagentx.api.service;

import com.darajaagentx.api.config.MailProperties;
import com.darajaagentx.api.mail.MailTemplates;
import com.darajaagentx.api.mail.OutgoingMail;
import com.darajaagentx.api.model.UserProfile;
import com.darajaagentx.api.repository.UserProfileRepository;
import com.darajaagentx.api.util.PersonNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The automatic emails a customer gets: the code to verify an address, a welcome, a new sign in, a finished payment.
 * Notices go only to a verified address, only when the server setting and the customer's own switch both allow them, and
 * never hold up or break what they are about: every failure is logged and swallowed. The email code is the exception,
 * it is sent at once and the caller learns if it failed.
 */
@Service
public class CustomerMailer {

    private static final Logger log = LoggerFactory.getLogger(CustomerMailer.class);

    private final MailOutbox outbox;
    private final UserProfileRepository profiles;
    private final MailProperties props;
    private final String brand;
    private final Clock clock;

    public CustomerMailer(MailOutbox outbox, UserProfileRepository profiles, MailProperties props,
                          @Value("${agentx.app-name:Daraja Agent-X}") String brand, Clock clock) {
        this.outbox = outbox;
        this.profiles = profiles;
        this.props = props;
        this.brand = brand;
        this.clock = clock;
    }

    public String brand() {
        return brand;
    }

    /** The code that proves an address is the customer's. Sent now, errors reach the caller. */
    public void emailCode(UserProfile profile, String address, String code) {
        MailTemplates.Message m = MailTemplates.emailCode(brand, firstName(profile), code, (int) props.codeTtl().toMinutes(), clock.instant());
        outbox.send(profile.getPhone(), "EMAIL_CODE", OutgoingMail.of(List.of(address), m.subject(), m.text(), m.html()));
    }

    /** After the address is verified for the first time. */
    public void welcome(UserProfile profile) {
        if (!props.notifications().welcome() || !hasVerifiedEmail(profile)) {
            return;
        }
        MailTemplates.Message m = MailTemplates.welcome(brand, firstName(profile), clock.instant());
        outbox.sendQuietly(profile.getPhone(), "WELCOME", OutgoingMail.of(List.of(profile.getEmail()), m.subject(), m.text(), m.html()));
    }

    /** A sign in happened. At most one notice per sign-in-alert-gap, so reconnecting does not flood an inbox. */
    public void signedIn(String phone, String deviceName) {
        try {
            if (!props.notifications().signIn()) {
                return;
            }
            Optional<UserProfile> found = profiles.findByPhone(phone);
            if (found.isEmpty()) {
                return;
            }
            UserProfile p = found.get();
            Instant now = clock.instant();
            if (!hasVerifiedEmail(p) || !p.isNotifySignIn()) {
                return;
            }
            if (p.getLastSignInMailAt() != null && now.isBefore(p.getLastSignInMailAt().plus(props.signInAlertGap()))) {
                return;
            }
            p.setLastSignInMailAt(now);
            profiles.save(p);
            MailTemplates.Message m = MailTemplates.signIn(brand, firstName(p), deviceName, now);
            outbox.sendQuietly(phone, "SIGN_IN", OutgoingMail.of(List.of(p.getEmail()), m.subject(), m.text(), m.html()));
        } catch (RuntimeException e) {
            log.warn("Sign in notice failed: {}", e.getMessage());
        }
    }

    /** A payment from the app (wallet action) was reported done. */
    public void paymentDone(String phone, String title, String summary, String receipt) {
        notifyPayment(phone, true, title, summary, null, receipt);
    }

    /** A business request ended. Only SUCCEEDED and FAILED are news; declined and expired are the customer's own doing or a timeout. */
    public void operationFinished(String phone, String status, String title, String summary, String result, String receipt) {
        try {
            if (!"SUCCEEDED".equals(status) && !"FAILED".equals(status)) {
                return;
            }
            notifyPayment(phone, "SUCCEEDED".equals(status), title, summary, result, receipt);
        } catch (RuntimeException e) {
            log.warn("Payment notice failed: {}", e.getMessage());
        }
    }

    /** Tells the owner that a report went to other people. */
    public void reportSentElsewhere(UserProfile owner, String title, List<String> recipients, String fileName) {
        if (!hasVerifiedEmail(owner)) {
            return;
        }
        MailTemplates.Message m = MailTemplates.reportNotice(brand, firstName(owner), title, recipients, fileName, clock.instant());
        outbox.sendQuietly(owner.getPhone(), "REPORT_NOTICE", OutgoingMail.of(List.of(owner.getEmail()), m.subject(), m.text(), m.html()));
    }

    private void notifyPayment(String phone, boolean ok, String title, String summary, String result, String receipt) {
        if (!props.notifications().payments()) {
            return;
        }
        Optional<UserProfile> found = profiles.findByPhone(phone);
        if (found.isEmpty() || !hasVerifiedEmail(found.get()) || !found.get().isNotifyPayments()) {
            return;
        }
        UserProfile p = found.get();
        MailTemplates.Message m = MailTemplates.payment(brand, firstName(p), ok, title, summary, result, receipt, clock.instant());
        outbox.sendQuietly(phone, "PAYMENT", OutgoingMail.of(List.of(p.getEmail()), m.subject(), m.text(), m.html()));
    }

    static boolean hasVerifiedEmail(UserProfile p) {
        return p.isEmailVerified() && p.getEmail() != null && !p.getEmail().isBlank();
    }

    private static String firstName(UserProfile p) {
        return p.getFirstName() == null ? "" : PersonNames.full(p.getFirstName(), null, null);
    }
}