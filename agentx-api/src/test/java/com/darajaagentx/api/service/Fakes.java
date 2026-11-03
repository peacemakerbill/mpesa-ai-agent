package com.darajaagentx.api.service;

import com.darajaagentx.api.config.MailProperties;
import com.darajaagentx.api.mail.MailDeliveryException;
import com.darajaagentx.api.mail.MailTransport;
import com.darajaagentx.api.mail.OutgoingMail;
import com.darajaagentx.api.model.EmailLog;
import com.darajaagentx.api.model.EmailRecipient;
import com.darajaagentx.api.model.UserProfile;
import com.darajaagentx.api.repository.EmailLogRepository;
import com.darajaagentx.api.repository.EmailRecipientRepository;
import com.darajaagentx.api.repository.UserProfileRepository;
import com.darajaagentx.api.storage.FileStore;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** In memory stand-ins for the stores, the mail server and the file store, shared by the tests of the profile and email code. */
final class Fakes {

    private Fakes() {
    }

    /** A clock the test can move. */
    static final class TestClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-29T08:00:00Z"));

        void advance(Duration d) {
            now.set(now.get().plus(d));
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    /** A mail server that keeps what it was given. [fail] makes it refuse. */
    static final class Mailbox implements MailTransport {
        final List<OutgoingMail> sent = new ArrayList<>();
        boolean fail;
        boolean delivers = true;

        @Override
        public void send(OutgoingMail mail) {
            if (fail) {
                throw new MailDeliveryException("The mail server did not accept the email: 550 refused");
            }
            sent.add(mail);
        }

        @Override
        public String describe() {
            return "test mailbox";
        }

        @Override
        public boolean delivers() {
            return delivers;
        }

        /** The six digit code in the last email, or null. */
        String lastCode() {
            if (sent.isEmpty()) {
                return null;
            }
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("code is (\\d{6})").matcher(sent.get(sent.size() - 1).text());
            return m.find() ? m.group(1) : null;
        }
    }

    /** A file store in memory. */
    static final class MemoryFiles implements FileStore {
        final Map<String, byte[]> files = new LinkedHashMap<>();

        @Override
        public void put(String key, byte[] data) {
            files.put(key, data);
        }

        @Override
        public Optional<byte[]> get(String key) {
            return Optional.ofNullable(files.get(key));
        }

        @Override
        public void delete(String key) {
            files.remove(key);
        }

        @Override
        public String describe() {
            return "memory";
        }

        @Override
        public boolean healthy() {
            return true;
        }
    }

    static final class Profiles {
        final Map<UUID, UserProfile> rows = new LinkedHashMap<>();

        UserProfileRepository repo() {
            return (UserProfileRepository) Proxy.newProxyInstance(Fakes.class.getClassLoader(), new Class<?>[]{UserProfileRepository.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "save" -> {
                        UserProfile p = (UserProfile) args[0];
                        p.prepareForMongo(Instant.now());
                        rows.put(p.getId(), p);
                        return p;
                    }
                    case "findByPhone" -> {
                        return rows.values().stream().filter(p -> p.getPhone().equals(args[0])).findFirst();
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                }
            });
        }
    }

    static final class Recipients {
        final Map<UUID, EmailRecipient> rows = new LinkedHashMap<>();

        EmailRecipientRepository repo() {
            return (EmailRecipientRepository) Proxy.newProxyInstance(Fakes.class.getClassLoader(), new Class<?>[]{EmailRecipientRepository.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "save" -> {
                        EmailRecipient r = (EmailRecipient) args[0];
                        r.prepareForMongo(Instant.now());
                        rows.put(r.getId(), r);
                        return r;
                    }
                    case "deleteAll" -> {
                        for (Object o : (Iterable<?>) args[0]) {
                            rows.remove(((EmailRecipient) o).getId());
                        }
                        return null;
                    }
                    case "findByOwnerPhoneOrderByCreatedAtAsc" -> {
                        return rows.values().stream().filter(r -> r.getOwnerPhone().equals(args[0])).toList();
                    }
                    case "findByIdAndOwnerPhone" -> {
                        return Optional.ofNullable(rows.get((UUID) args[0])).filter(r -> r.getOwnerPhone().equals(args[1]));
                    }
                    case "findByOwnerPhoneAndEmail" -> {
                        return rows.values().stream().filter(r -> r.getOwnerPhone().equals(args[0]) && r.getEmail().equals(args[1])).findFirst();
                    }
                    case "countByOwnerPhone" -> {
                        return rows.values().stream().filter(r -> r.getOwnerPhone().equals(args[0])).count();
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                }
            });
        }
    }

    static final class Logs {
        final List<EmailLog> rows = new ArrayList<>();

        EmailLogRepository repo() {
            return (EmailLogRepository) Proxy.newProxyInstance(Fakes.class.getClassLoader(), new Class<?>[]{EmailLogRepository.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "save" -> {
                        EmailLog l = (EmailLog) args[0];
                        l.prepareForMongo(Instant.now());
                        rows.add(l);
                        return l;
                    }
                    case "findTop30ByOwnerPhoneOrderByCreatedAtDesc" -> {
                        return rows.stream().filter(l -> l.getOwnerPhone().equals(args[0])).sorted(Comparator.comparing(EmailLog::getCreatedAt).reversed()).toList();
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                }
            });
        }
    }

    static MailProperties mailProps() {
        return mailProps(true, List.of());
    }

    static MailProperties mailProps(boolean allowExternal, List<String> domains) {
        return new MailProperties("log", "no-reply@example.com", "Daraja Agent-X", "",
                new MailProperties.Smtp("", 587, "", "", true, false, Duration.ofSeconds(10), Duration.ofSeconds(30)),
                new MailProperties.Notifications(true, true, true),
                new MailProperties.Reports(10, 10, 5000, 8_388_608, allowExternal, domains),
                Duration.ofMinutes(10), 5, 5, Duration.ofMinutes(30));
    }

    /** Everything wired together the way the application wires it, with in memory stores. */
    static final class Rig {
        final TestClock clock = new TestClock();
        final Mailbox mailbox = new Mailbox();
        final Logs logs = new Logs();
        final Profiles profiles = new Profiles();
        final Recipients recipients = new Recipients();
        final MemoryFiles files = new MemoryFiles();
        final SlidingWindowLimiter limiter = new SlidingWindowLimiter(clock);
        final MailProperties props;
        final MailOutbox outbox;
        final CustomerMailer mailer;
        final ProfileService service;

        Rig() {
            this(mailProps(), false);
        }

        Rig(MailProperties props, boolean exposeCodes) {
            this(props, exposeCodes, true);
        }

        Rig(MailProperties props, boolean exposeCodes, boolean emailDelivers) {
            this.props = props;
            outbox = new MailOutbox(mailbox, logs.repo(), props, Runnable::run);
            mailer = new CustomerMailer(outbox, profiles.repo(), props, "Daraja Agent-X", clock);
            service = new ProfileService(profiles.repo(), files, 3_145_728, props, mailer, limiter, "test-secret-test-secret-test-secret!", exposeCodes, emailDelivers, clock);
        }

        /** An active profile with a verified email. */
        UserProfile verified(String phone, String email) {
            service.getOrCreate(phone);
            service.update(phone, new com.darajaagentx.api.dto.ProfileUpdate("Amina", null, "Otieno", null, null, null, null, null, null, null, null, null));
            service.requestEmailCode(phone, email);
            service.verifyEmail(phone, mailbox.lastCode());
            mailbox.sent.clear();
            return profiles.repo().findByPhone(phone).orElseThrow();
        }
    }
}
