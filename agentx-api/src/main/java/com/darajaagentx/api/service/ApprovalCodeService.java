package com.darajaagentx.api.service;

import com.darajaagentx.api.config.AgentXProperties;
import com.darajaagentx.api.dto.ApprovalCodeResponse;
import com.darajaagentx.api.exception.CodeDeliveryException;
import com.darajaagentx.api.exception.InvalidActionRequestException;
import com.darajaagentx.api.exception.InvalidActionStateException;
import com.darajaagentx.api.exception.TooManyRequestsException;
import com.darajaagentx.api.model.ActionStatus;
import com.darajaagentx.api.model.ApprovalCode;
import com.darajaagentx.api.model.BusinessOperation;
import com.darajaagentx.api.model.BusinessOperationStatus;
import com.darajaagentx.api.model.WalletAction;
import com.darajaagentx.api.repository.ApprovalCodeRepository;
import com.darajaagentx.api.util.Hmac;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Approving a request with a code sent by text message, for customers whose phone cannot do a fingerprint, face or
 * screen lock check, or who would rather not.
 *
 * The code goes to the customer's own number (the one in the login token), names what it approves, works for that one
 * request, lives five minutes and allows five wrong tries. Asking again replaces the code, at most once every 30
 * seconds and six times an hour. A code is used up the moment it is right.
 */
@Service
public class ApprovalCodeService {

    public enum Kind { ACTION, BUSINESS }

    static final Duration TTL = Duration.ofMinutes(5);
    static final Duration RESEND_GAP = Duration.ofSeconds(30);
    static final int MAX_ATTEMPTS = 5;
    static final int PER_HOUR = 6;
    static final String INVALID = "That code is not right or has expired. Ask for a new one.";

    private static final Pattern SIX_DIGITS = Pattern.compile("^\\d{6}$");

    private final ApprovalCodeRepository codes;
    private final ActionService actions;
    private final BusinessOperationService business;
    private final CodeDelivery delivery;
    private final AgentXProperties props;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public ApprovalCodeService(ApprovalCodeRepository codes, ActionService actions, BusinessOperationService business,
                               CodeDelivery delivery, AgentXProperties props, Clock clock) {
        this.codes = codes;
        this.actions = actions;
        this.business = business;
        this.delivery = delivery;
        this.props = props;
        this.clock = clock;
    }

    /** Sends a code for a personal request that is still waiting for approval. */
    public ApprovalCodeResponse sendForAction(String phone, UUID id) {
        WalletAction a = actions.get(id, phone);
        if (a.getStatus() != ActionStatus.CREATED) {
            throw new InvalidActionStateException("This request is no longer waiting for approval.");
        }
        return send(phone, Kind.ACTION, id, ActionSummary.summary(a));
    }

    /** Sends a code for a request on a business account that is still waiting for approval. */
    public ApprovalCodeResponse sendForBusiness(String phone, UUID id) {
        BusinessOperation op = business.get(phone, id);
        if (op.getStatus() != BusinessOperationStatus.PENDING_APPROVAL) {
            throw new InvalidActionStateException("This request is no longer waiting for approval.");
        }
        return send(phone, Kind.BUSINESS, id, op.getSummary());
    }

    private ApprovalCodeResponse send(String phone, Kind kind, UUID id, String what) {
        Instant now = clock.instant();
        String k = kind.name();
        Optional<ApprovalCode> newest = codes.findFirstByPhoneAndKindAndTargetIdOrderByCreatedAtDesc(phone, k, id);
        if (newest.isPresent() && now.isBefore(newest.get().getCreatedAt().plus(RESEND_GAP))) {
            throw new TooManyRequestsException("A code was just sent. Wait a few seconds before asking for another.");
        }
        if (codes.countByPhoneAndCreatedAtAfter(phone, now.minus(Duration.ofHours(1))) >= PER_HOUR) {
            throw new TooManyRequestsException("Too many codes were requested. Please try again in an hour.");
        }
        // Only the newest code works
        for (ApprovalCode old : codes.findByPhoneAndKindAndTargetIdAndUsedAtIsNull(phone, k, id)) {
            old.setUsedAt(now);
            codes.save(old);
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        Instant expires = now.plus(TTL);
        ApprovalCode saved = codes.save(new ApprovalCode(phone, k, id, hash(phone, k, id, code), expires, now));
        try {
            CodeDelivery.Result sent = delivery.deliverApproval(phone, code, TTL, what);
            return new ApprovalCodeResponse(expires, (int) RESEND_GAP.toSeconds(), sent.devCode());
        } catch (CodeDeliveryException e) {
            saved.setUsedAt(now);
            codes.save(saved);
            throw e;
        }
    }

    /**
     * Checks the code and uses it up. A wrong try is remembered even though the request fails, so this does not
     * roll back on a wrong code. Call it before approving, and not inside another transaction.
     */
    @Transactional(noRollbackFor = InvalidActionRequestException.class)
    public void check(String phone, Kind kind, UUID id, String code) {
        String entered = code == null ? "" : code.strip();
        if (!SIX_DIGITS.matcher(entered).matches()) {
            throw new InvalidActionRequestException("Enter the 6 digit code from the text message.");
        }
        Instant now = clock.instant();
        String k = kind.name();
        ApprovalCode stored = codes.findFirstByPhoneAndKindAndTargetIdAndUsedAtIsNullOrderByCreatedAtDesc(phone, k, id)
                .filter(c -> now.isBefore(c.getExpiresAt()))
                .orElseThrow(() -> new InvalidActionRequestException(INVALID));
        if (stored.getAttempts() >= MAX_ATTEMPTS) {
            stored.setUsedAt(now);
            codes.save(stored);
            throw new InvalidActionRequestException(INVALID);
        }
        stored.setAttempts(stored.getAttempts() + 1);
        boolean match = MessageDigest.isEqual(stored.getCodeHash().getBytes(StandardCharsets.UTF_8),
                hash(phone, k, id, entered).getBytes(StandardCharsets.UTF_8));
        if (!match) {
            if (stored.getAttempts() >= MAX_ATTEMPTS) {
                stored.setUsedAt(now);
            }
            codes.save(stored);
            throw new InvalidActionRequestException(INVALID);
        }
        stored.setUsedAt(now);
        codes.save(stored);
    }

    private String hash(String phone, String kind, UUID id, String code) {
        return Hmac.hex("approval-code:" + props.tokenSecret(), phone + ":" + kind + ":" + id + ":" + code);
    }
}
