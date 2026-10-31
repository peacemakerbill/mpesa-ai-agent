package com.mpesaagent.companion.service;

import com.mpesaagent.companion.config.CompanionProperties;
import com.mpesaagent.companion.dto.ApproveRequest;
import com.mpesaagent.companion.dto.CreateActionRequest;
import com.mpesaagent.companion.dto.ReportRequest;
import com.mpesaagent.companion.dto.SmsEvidenceRequest;
import com.mpesaagent.companion.model.Verification;
import com.mpesaagent.companion.event.ActionEvent;
import com.mpesaagent.companion.exception.ActionNotFoundException;
import com.mpesaagent.companion.exception.InvalidActionRequestException;
import com.mpesaagent.companion.exception.InvalidActionStateException;
import com.mpesaagent.companion.exception.TooManyRequestsException;
import com.mpesaagent.companion.model.ActionStatus;
import com.mpesaagent.companion.model.ActionSource;
import com.mpesaagent.companion.model.WalletAction;
import com.mpesaagent.companion.repository.WalletActionRepository;
import com.mpesaagent.companion.util.Phones;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * The life of a wallet action. We never move money: the customer does it in M-Pesa on their own phone.
 * Every method takes the customer's phone and treats an action that belongs to someone else as not found.
 */
@Service
public class ActionService {

    private static final Pattern RECEIPT = Pattern.compile("^[A-Z0-9]{8,12}$");

    private final WalletActionRepository repo;
    private final ActionValidator validator;
    private final CompanionProperties props;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public ActionService(WalletActionRepository repo, ActionValidator validator, CompanionProperties props,
                         Clock clock, ApplicationEventPublisher events) {
        this.repo = repo;
        this.validator = validator;
        this.props = props;
        this.clock = clock;
        this.events = events;
    }

    // ------------------------------------------------------------ agent side

    @Transactional
    public WalletAction create(CreateActionRequest r) {
        String phone = Phones.normalize(r.phone());
        Optional<WalletAction> existing = repo.findByIdempotencyKey(r.idempotencyKey());
        if (existing.isPresent()) {
            WalletAction a = existing.get();
            if (!a.getPhone().equals(phone) || a.getType() != r.type() || a.getAmount() != r.amount()) {
                throw new InvalidActionStateException("That request id was already used for a different request");
            }
            return expireIfDue(a);
        }
        ActionValidator.Validated v = validator.validate(phone, r.type(), r.amount(), r.details(), r.label());

        expireStale(phone);
        long open = repo.countByPhoneAndStatusIn(phone, EnumSet.of(ActionStatus.CREATED, ActionStatus.APPROVED, ActionStatus.HANDED_OFF));
        if (open >= props.maxOpenActions()) {
            throw new TooManyRequestsException("There are too many unfinished requests. Finish or cancel one first.");
        }
        ActionSource source = r.source() == null ? ActionSource.TEXT : r.source();
        WalletAction action = new WalletAction(r.idempotencyKey(), phone, r.type(), v.amount(), v.label(), source,
                v.details(), clock.instant().plus(props.actionTtl()));
        return repo.save(action);
    }

    @Transactional
    public List<WalletAction> list(String rawPhone, int limit) {
        String phone = Phones.normalize(rawPhone);
        expireStale(phone);
        return repo.findByPhoneOrderByCreatedAtDesc(phone, PageRequest.of(0, Math.max(1, Math.min(limit, 50))));
    }

    @Transactional
    public WalletAction get(UUID id, String rawPhone) {
        WalletAction a = repo.findById(id).filter(x -> x.getPhone().equals(Phones.normalize(rawPhone)))
                .orElseThrow(() -> new ActionNotFoundException("Request not found"));
        return expireIfDue(a);
    }

    /** Cancel from the chat. Only possible before the customer has opened M-Pesa. */
    @Transactional
    public WalletAction cancel(UUID id, String phone) {
        return move(id, phone, ActionStatus.CANCELLED, a -> {
        });
    }

    /** The customer forwarded the M-Pesa confirmation to the chat. We keep the code, we cannot verify it. */
    @Transactional
    public WalletAction attachReceipt(UUID id, String phone, String rawReceipt) {
        String receipt = cleanReceipt(rawReceipt);
        WalletAction a = lock(id, phone);
        if (a.getStatus() != ActionStatus.HANDED_OFF && a.getStatus() != ActionStatus.DONE_REPORTED
                && a.getStatus() != ActionStatus.APPROVED) {
            throw new InvalidActionStateException("This request was not completed in the app, so a confirmation cannot be added");
        }
        if (a.getReceipt() != null && !a.getReceipt().equals(receipt)) {
            throw new InvalidActionStateException("This request already has a different confirmation code");
        }
        if (a.getStatus() != ActionStatus.DONE_REPORTED) {
            a.setStatus(ActionStatus.DONE_REPORTED);
            a.setReportedAt(clock.instant());
        }
        a.setReceipt(receipt);
        a.raiseVerification(Verification.REPORTED);
        return repo.save(a);
    }

    // ------------------------------------------------------------ app side

    @Transactional
    public WalletAction approve(UUID id, String phone, String deviceId, ApproveRequest.Method method) {
        return move(id, phone, ActionStatus.APPROVED, a -> {
            a.setApprovedDeviceId(deviceId);
            a.setApprovedAt(clock.instant());
        });
    }

    @Transactional
    public WalletAction handOff(UUID id, String phone) {
        return move(id, phone, ActionStatus.HANDED_OFF, a -> {
        });
    }

    @Transactional
    public WalletAction report(UUID id, String phone, ReportRequest.Outcome outcome, String rawReceipt) {
        String receipt = rawReceipt == null || rawReceipt.isBlank() ? null : cleanReceipt(rawReceipt);
        ActionStatus next = outcome == ReportRequest.Outcome.DONE ? ActionStatus.DONE_REPORTED : ActionStatus.NOT_DONE;
        return move(id, phone, next, a -> {
            a.setReportedAt(clock.instant());
            if (next == ActionStatus.DONE_REPORTED) {
                a.raiseVerification(Verification.REPORTED);
                if (receipt != null) {
                    a.setReceipt(receipt);
                }
            }
        });
    }

    /**
     * The app read an M-Pesa message on the customer's phone and found one that fits this request. It may arrive
     * after the customer said "done" (this makes that stronger) or even after they said "not done" (money left,
     * so the record is corrected). The request ends up completed, backed by a message from the customer's own
     * phone. It is still not a confirmation from Safaricom.
     */
    @Transactional
    public WalletAction confirmWithSms(UUID id, String phone, SmsEvidenceRequest sms) {
        WalletAction a = lock(id, phone);
        expireIfDue(a);
        ActionStatus st = a.getStatus();
        if (st != ActionStatus.APPROVED && st != ActionStatus.HANDED_OFF && st != ActionStatus.DONE_REPORTED
                && st != ActionStatus.NOT_DONE) {
            throw new InvalidActionStateException("This request is " + words(st) + ", so a message cannot be matched to it");
        }
        if (a.getSmsCode() != null) {
            if (a.getSmsCode().equals(sms.code())) {
                return a;
            }
            throw new InvalidActionStateException("This request is already matched to a different M-Pesa message");
        }
        if (repo.existsBySmsCode(sms.code())) {
            throw new InvalidActionStateException("That M-Pesa message was already used for another request");
        }
        Verification level = SmsMatcher.match(a, sms, clock.instant());

        a.setStatus(ActionStatus.DONE_REPORTED);
        if (a.getReportedAt() == null || st == ActionStatus.NOT_DONE) {
            a.setReportedAt(clock.instant());
        }
        a.setReceipt(sms.code());
        a.recordSms(sms.code(), clock.instant());
        a.raiseVerification(level);
        repo.save(a);
        events.publishEvent(new ActionEvent(a.getId(), a.getPhone(), a.getType().name(), a.getStatus().name(), a.getAmount(),
                ActionSummary.summary(a), a.getReceipt(), a.getVerification().name(), clock.instant()));
        return a;
    }

    @Transactional
    public WalletAction decline(UUID id, String phone) {
        return move(id, phone, ActionStatus.DECLINED, a -> {
        });
    }

    // ------------------------------------------------------------ internals

    /** Moves to the next state once. Repeating the same move (a retried request) is not an error. */
    private WalletAction move(UUID id, String phone, ActionStatus next, Consumer<WalletAction> change) {
        WalletAction a = lock(id, phone);
        expireIfDue(a);
        if (a.getStatus() == next) {
            return a;
        }
        if (!a.getStatus().canMoveTo(next)) {
            throw new InvalidActionStateException("This request is " + words(a.getStatus()) + " and can no longer be changed that way");
        }
        a.setStatus(next);
        change.accept(a);
        repo.save(a);
        if (next == ActionStatus.DONE_REPORTED || next == ActionStatus.NOT_DONE || next == ActionStatus.DECLINED) {
            events.publishEvent(new ActionEvent(a.getId(), a.getPhone(), a.getType().name(), next.name(), a.getAmount(),
                    ActionSummary.summary(a), a.getReceipt(), a.getVerification().name(), clock.instant()));
        }
        return a;
    }

    private WalletAction lock(UUID id, String rawPhone) {
        String phone = Phones.normalize(rawPhone);
        return repo.findByIdForUpdate(id).filter(a -> a.getPhone().equals(phone))
                .orElseThrow(() -> new ActionNotFoundException("Request not found"));
    }

    private WalletAction expireIfDue(WalletAction a) {
        if (a.getStatus().canExpire() && !clock.instant().isBefore(a.getExpiresAt())) {
            a.setStatus(ActionStatus.EXPIRED);
            repo.save(a);
        }
        return a;
    }

    private void expireStale(String phone) {
        repo.findByPhoneOrderByCreatedAtDesc(phone, PageRequest.of(0, 50)).forEach(this::expireIfDue);
    }

    private static String cleanReceipt(String raw) {
        String receipt = raw == null ? "" : raw.trim().toUpperCase(Locale.ENGLISH);
        if (!RECEIPT.matcher(receipt).matches()) {
            throw new InvalidActionRequestException("An M-Pesa confirmation code has 10 letters and digits, for example SGL1234567");
        }
        return receipt;
    }

    private static String words(ActionStatus s) {
        return switch (s) {
            case CREATED -> "waiting";
            case APPROVED -> "approved";
            case HANDED_OFF -> "with M-Pesa";
            case DONE_REPORTED -> "completed";
            case NOT_DONE -> "marked as not done";
            case DECLINED -> "declined";
            case CANCELLED -> "cancelled";
            case EXPIRED -> "expired";
        };
    }
}
