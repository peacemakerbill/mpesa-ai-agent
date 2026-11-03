package com.darajaagentx.api.service;

import com.darajaagentx.api.config.BusinessOpsProperties;
import com.darajaagentx.api.config.AgentXProperties;
import com.darajaagentx.api.dto.BusinessAccountView;
import com.darajaagentx.api.exception.ActionNotFoundException;
import com.darajaagentx.api.exception.CertificateMissingException;
import com.darajaagentx.api.exception.DarajaOutcomeUnknownException;
import com.darajaagentx.api.exception.DarajaRefusedException;
import com.darajaagentx.api.exception.InvalidActionRequestException;
import com.darajaagentx.api.exception.InvalidActionStateException;
import com.darajaagentx.api.exception.TooManyRequestsException;
import com.darajaagentx.api.exception.VaultNotConfiguredException;
import com.darajaagentx.api.model.BusinessOperation;
import com.darajaagentx.api.model.BusinessOperationStatus;
import com.darajaagentx.api.model.BusinessOperationType;
import com.darajaagentx.api.repository.BusinessOperationRepository;
import com.darajaagentx.api.util.Phones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Makes business requests from the chat: prepare, approve in the app, send to Daraja, and take Safaricom's answer.
 *
 * <p>Rules that keep money safe:
 * <ul>
 *   <li>The owner's phone number always comes from the signed-in chat or app, never from the model.</li>
 *   <li>Payouts, B2B and reversals wait for the owner's approval in the app. STK Push can be made to wait too.</li>
 *   <li>A request is sent to Safaricom at most once. If the answer is unclear it becomes UNKNOWN and is never retried.</li>
 *   <li>Safaricom's callback is accepted only with the secret token that is part of the URL given for that one request.</li>
 *   <li>Secrets are unlocked only for the moment of the call and are not stored here or logged.</li>
 * </ul>
 */
@Service
public class BusinessOperationService {

    private static final Logger log = LoggerFactory.getLogger(BusinessOperationService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern CODE = Pattern.compile("^\\d{5,7}$");
    private static final Pattern RECEIPT = Pattern.compile("^[A-Z0-9]{8,12}$");
    private static final Pattern REFERENCE = Pattern.compile("^[A-Za-z0-9]{1,12}$");
    private static final DateTimeFormatter PULL_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Set<String> B2C_COMMANDS = Set.of("BusinessPayment", "SalaryPayment", "PromotionPayment");

    /** What preparing an operation gave back. [qrCode] is set only for a Dynamic QR and is not stored anywhere. */
    public record Prepared(BusinessOperation operation, String qrCode) {
    }

    private record Plan(int amount, String summary, Map<String, String> params) {
    }

    private final BusinessOperationRepository ops;
    private final BusinessAccountService accounts;
    private final DarajaApi daraja;
    private final ChatNotifier notifier;
    private final BusinessOpsProperties props;
    private final String publicBaseUrl;
    private final SlidingWindowLimiter limiter;
    private final Clock clock;
    private final ConcurrentHashMap<UUID, Object> locks = new ConcurrentHashMap<>();
    /** Told when a request ends, for example to send an email notice. Optional: tests and a server without mail leave it empty. */
    private OperationFinishedHook finishedHook = op -> { };

    @Autowired
    public BusinessOperationService(BusinessOperationRepository ops, BusinessAccountService accounts, DarajaApi daraja,
                                    ChatNotifier notifier, BusinessOpsProperties props, AgentXProperties agentx,
                                    SlidingWindowLimiter limiter, Clock clock) {
        this(ops, accounts, daraja, notifier, props, agentx.publicBaseUrl(), limiter, clock);
    }

    BusinessOperationService(BusinessOperationRepository ops, BusinessAccountService accounts, DarajaApi daraja,
                             ChatNotifier notifier, BusinessOpsProperties props, String publicBaseUrl,
                             SlidingWindowLimiter limiter, Clock clock) {
        this.ops = ops;
        this.accounts = accounts;
        this.daraja = daraja;
        this.notifier = notifier;
        this.props = props;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        this.limiter = limiter;
        this.clock = clock;
    }

    @Autowired(required = false)
    public void setFinishedHook(OperationFinishedHook hook) {
        this.finishedHook = hook == null ? op -> { } : hook;
    }

    // ------------------------------------------------------------------ reading

    public List<BusinessOperation> list(String owner) {
        List<BusinessOperation> all = ops.findTop30ByOwnerPhoneOrderByCreatedAtDesc(owner);
        all.forEach(this::expireIfStale);
        return all;
    }

    public BusinessOperation get(String owner, UUID id) {
        return expireIfStale(require(owner, id));
    }

    public boolean approvalRequired(BusinessOperation op) {
        return op.getType().requiresApproval(props.approveCollections());
    }

    // ------------------------------------------------------------------ prepare

    /**
     * Checks the request and saves it. A request that needs approval waits for the owner. Any other is sent to Safaricom
     * at once.
     *
     * @param accountRef     the account by name, till, shortcode or id. May be left out when the owner has only one.
     * @param idempotencyKey the same key returns the same operation, so a retried model call never sends twice
     */
    public Prepared prepare(String owner, String accountRef, String typeText, Map<String, String> raw, String source, String idempotencyKey) {
        String key = blank(idempotencyKey) ? null : trim(idempotencyKey.trim(), 64);
        if (key != null) {
            Optional<BusinessOperation> existing = ops.findByOwnerPhoneAndIdempotencyKey(owner, key);
            if (existing.isPresent()) {
                return new Prepared(expireIfStale(existing.get()), null);
            }
        }
        BusinessOperationType type = BusinessOperationType.parse(typeText);
        if (type == null) {
            throw new InvalidActionRequestException("That kind of request is not supported. It can be one of: "
                    + java.util.Arrays.stream(BusinessOperationType.values()).map(Enum::name).collect(Collectors.joining(", ")) + ".");
        }
        BusinessAccountView account = findAccount(owner, accountRef);
        requireCapability(account, type);
        Plan plan = plan(owner, type, account, raw == null ? Map.of() : raw);

        boolean approval = approvalRequired(type);
        if (type == BusinessOperationType.STK_PUSH && !limiter.allow("biz-stk:" + owner, props.stkPerHour(), Duration.ofHours(1))) {
            throw new TooManyRequestsException("Too many STK Push requests this hour. Please wait a while.");
        }
        if (approval && ops.findByOwnerPhoneAndStatus(owner, BusinessOperationStatus.PENDING_APPROVAL).stream()
                .filter(o -> !isStale(o)).count() >= props.maxOpenApprovals()) {
            throw new InvalidActionRequestException("Too many requests are waiting for your approval. Approve or decline some in the app first.");
        }

        Instant now = clock.instant();
        BusinessOperation op = new BusinessOperation(owner, account.id(), account.label(), type);
        op.setAmount(plan.amount());
        op.setSummary(trim(plan.summary(), 300));
        op.setParams(plan.params());
        op.setSource(blank(source) ? "TEXT" : trim(source.trim().toUpperCase(Locale.ROOT), 12));
        op.setIdempotencyKey(key == null ? "k-" + UUID.randomUUID() : key);
        op.setCallbackToken(newToken());
        if (approval) {
            op.setStatus(BusinessOperationStatus.PENDING_APPROVAL);
            op.setExpiresAt(now.plus(props.approvalTtl()));
            return new Prepared(ops.save(op), null);
        }
        op.setStatus(BusinessOperationStatus.SUBMITTED);
        op.setApprovedAt(now);
        op.setApprovalMethod("NOT_REQUIRED");
        BusinessOperation saved = ops.save(op);
        // Held while the request goes out, so an early callback from Safaricom waits and is never overwritten by this call
        synchronized (lockFor(saved.getId())) {
            return run(saved, false);
        }
    }

    private boolean approvalRequired(BusinessOperationType type) {
        return type.requiresApproval(props.approveCollections());
    }

    // ------------------------------------------------------------------ approve and decline

    /** The owner approved in the app. The request is sent to Safaricom once, here. */
    public BusinessOperation approve(String owner, UUID id, String method) {
        synchronized (lockFor(id)) {
            BusinessOperation op = require(owner, id);
            expireIfStale(op);
            if (op.getStatus() == BusinessOperationStatus.EXPIRED) {
                throw new InvalidActionStateException("This request expired. Ask for it again in the chat.");
            }
            if (op.getStatus() != BusinessOperationStatus.PENDING_APPROVAL) {
                throw new InvalidActionStateException("This request is no longer waiting for approval.");
            }
            op.setStatus(BusinessOperationStatus.SUBMITTED);
            op.setApprovedAt(clock.instant());
            op.setApprovalMethod(blank(method) ? "UNKNOWN" : trim(method, 20));
            return run(ops.save(op), true).operation();
        }
    }

    public BusinessOperation decline(String owner, UUID id) {
        synchronized (lockFor(id)) {
            BusinessOperation op = require(owner, id);
            expireIfStale(op);
            if (op.getStatus() != BusinessOperationStatus.PENDING_APPROVAL) {
                throw new InvalidActionStateException("This request is no longer waiting for approval.");
            }
            op.setStatus(BusinessOperationStatus.DECLINED);
            op.setCompletedAt(clock.instant());
            op.setResultText("Declined. Nothing was sent to Safaricom.");
            BusinessOperation saved = ops.save(op);
            notifier.text(owner, "You declined this, so nothing was sent:\n\n" + saved.getSummary());
            return saved;
        }
    }

    // ------------------------------------------------------------------ sending

    private Prepared run(BusinessOperation op, boolean tellChat) {
        String owner = op.getOwnerPhone();
        try {
            ResolvedCredentials c = accounts.resolve(owner, op.getAccountId());
            DarajaRequests.Call call = build(op, c);
            Map<String, Object> answer = daraja.post(c.environment(), c.consumerKey(), c.consumerSecret(), call.path(), call.body());
            return afterAnswer(op, answer, tellChat);
        } catch (DarajaRefusedException e) {
            if (op.getType() == BusinessOperationType.STK_QUERY && e.getMessage().toLowerCase(Locale.ROOT).contains("being processed")) {
                return new Prepared(finish(op, BusinessOperationStatus.SUCCEEDED,
                        "The customer has not finished yet. The prompt is still on their phone.", null, tellChat), null);
            }
            return new Prepared(fail(op, e.getMessage(), tellChat), null);
        } catch (InvalidActionRequestException | ActionNotFoundException | VaultNotConfiguredException | CertificateMissingException e) {
            return new Prepared(fail(op, e.getMessage(), tellChat), null);
        } catch (DarajaOutcomeUnknownException e) {
            op.setFailure(trim(e.getMessage() + " It may or may not have gone through. Check your M-Pesa statement before trying again.", 500));
            op.setStatus(BusinessOperationStatus.UNKNOWN);
            BusinessOperation saved = ops.save(op);
            if (tellChat) {
                notifier.text(owner, "*Not sure if this went through*\n\n" + saved.getSummary() + "\n\n" + saved.getFailure());
            }
            return new Prepared(saved, null);
        }
    }

    private DarajaRequests.Call build(BusinessOperation op, ResolvedCredentials c) {
        String owner = op.getOwnerPhone();
        Map<String, String> p = op.getParams();
        String base = publicBaseUrl + "/daraja/business/" + op.getId() + "/" + op.getCallbackToken();
        String result = base + "/result";
        String timeout = base + "/timeout";
        return switch (op.getType()) {
            case STK_PUSH -> {
                if (c.passkey() == null) {
                    throw new InvalidActionRequestException("This business account has no STK passkey saved.");
                }
                yield DarajaRequests.stkPush(c, p.get("phone"), op.getAmount(), p.get("reference"), p.get("description"), base + "/callback", clock.instant());
            }
            case STK_QUERY -> {
                if (c.passkey() == null) {
                    throw new InvalidActionRequestException("This business account has no STK passkey saved.");
                }
                yield DarajaRequests.stkQuery(c, p.get("checkoutRequestId"), clock.instant());
            }
            case DYNAMIC_QR -> DarajaRequests.qr(c, p.get("merchantName"), p.get("reference"), op.getAmount());
            case PULL -> DarajaRequests.pull(c, p.get("start"), p.get("end"), Integer.parseInt(p.getOrDefault("offset", "0")));
            case B2C -> {
                op.setOriginatorConversationId(UUID.randomUUID().toString());
                yield DarajaRequests.b2c(c, accounts.securityCredential(owner, op.getAccountId()), p.get("command"), op.getOriginatorConversationId(),
                        p.get("phone"), op.getAmount(), p.get("remarks"), p.get("occasion"), result, timeout);
            }
            case B2POCHI -> {
                op.setOriginatorConversationId(UUID.randomUUID().toString());
                yield DarajaRequests.b2pochi(c, accounts.securityCredential(owner, op.getAccountId()), op.getOriginatorConversationId(),
                        p.get("phone"), op.getAmount(), p.get("remarks"), p.get("occasion"), result, timeout);
            }
            case B2B -> DarajaRequests.b2b(c, accounts.securityCredential(owner, op.getAccountId()), p.get("command"), p.get("receiver"),
                    p.get("accountReference"), op.getAmount(), p.get("remarks"), result, timeout);
            case BALANCE -> DarajaRequests.balance(c, accounts.securityCredential(owner, op.getAccountId()), "Balance check", result, timeout);
            case STATUS -> DarajaRequests.status(c, accounts.securityCredential(owner, op.getAccountId()), p.get("receipt"), "Status check", result, timeout);
            case REVERSAL -> DarajaRequests.reversal(c, accounts.securityCredential(owner, op.getAccountId()), p.get("receipt"), op.getAmount(),
                    p.get("remarks"), result, timeout);
        };
    }

    private Prepared afterAnswer(BusinessOperation op, Map<String, Object> answer, boolean tellChat) {
        String code = str(answer.get("ResponseCode"));
        String description = firstNonBlank(str(answer.get("ResponseDescription")), str(answer.get("CustomerMessage")), str(answer.get("ResponseMessage")));
        switch (op.getType()) {
            case DYNAMIC_QR -> {
                String qr = str(answer.get("QRCode"));
                if (blank(qr)) {
                    return new Prepared(fail(op, description == null ? "Safaricom did not return a QR code." : description, tellChat), null);
                }
                return new Prepared(finish(op, BusinessOperationStatus.SUCCEEDED, "QR code made for " + money(op.getAmount()) + ".", null, false), qr);
            }
            case STK_QUERY -> {
                String resultCode = str(answer.get("ResultCode"));
                if (resultCode == null) {
                    return new Prepared(fail(op, description == null ? "Safaricom gave no result for this payment yet." : description, tellChat), null);
                }
                boolean paid = "0".equals(resultCode);
                String text = paid ? "The customer paid." : "Not paid: " + firstNonBlank(str(answer.get("ResultDesc")), "the customer did not complete it") + ".";
                settleOrigin(op, paid, text);
                return new Prepared(finish(op, BusinessOperationStatus.SUCCEEDED, text, null, tellChat), null);
            }
            case PULL -> {
                // Safaricom's docs show 1000 for success, other APIs use 0 or 00. Rows in the answer are success too.
                boolean ok = code == null || Set.of("0", "00", "1000").contains(code) || !maps(answer.get("Response")).isEmpty();
                return new Prepared(finish(op, ok ? BusinessOperationStatus.SUCCEEDED : BusinessOperationStatus.FAILED,
                        pullText(answer), null, tellChat), null);
            }
            case STK_PUSH -> {
                if (!"0".equals(code)) {
                    return new Prepared(fail(op, description == null ? "Safaricom did not accept the STK Push." : description, tellChat), null);
                }
                op.setCheckoutRequestId(str(answer.get("CheckoutRequestID")));
                op.setResultText("The prompt was sent to " + op.getParams().get("phone") + ". Waiting for the customer to enter their PIN.");
                return new Prepared(ops.save(op), null);
            }
            default -> {
                if (!"0".equals(code)) {
                    return new Prepared(fail(op, description == null ? "Safaricom did not accept the request." : description, tellChat), null);
                }
                op.setConversationId(str(answer.get("ConversationID")));
                op.setResultText("Safaricom accepted the request. The result will arrive in this chat.");
                return new Prepared(ops.save(op), null);
            }
        }
    }

    /** An STK query that finds the answer also settles the STK Push it asked about. */
    private void settleOrigin(BusinessOperation query, boolean paid, String text) {
        String originId = query.getParams().get("operationId");
        if (blank(originId)) {
            return;
        }
        try {
            ops.findByIdAndOwnerPhone(UUID.fromString(originId), query.getOwnerPhone()).ifPresent(origin -> {
                if (!origin.getStatus().finished()) {
                    origin.setStatus(paid ? BusinessOperationStatus.SUCCEEDED : BusinessOperationStatus.FAILED);
                    origin.setResultText(text);
                    origin.setCompletedAt(clock.instant());
                    ops.save(origin);
                }
            });
        } catch (IllegalArgumentException ignored) {
            // not an id we made
        }
    }

    // ------------------------------------------------------------------ Safaricom's answers

    /**
     * Takes the answer Safaricom posts to one of the URLs given for a request.
     *
     * @param kind callback (STK Push), result, or timeout
     */
    public void onCallback(UUID id, String token, String kind, Map<String, Object> payload) {
        synchronized (lockFor(id)) {
            BusinessOperation op = ops.findById(id).orElse(null);
            if (op == null) {
                return;
            }
            if (token == null || !MessageDigest.isEqual(op.getCallbackToken().getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
                log.warn("A callback for business operation {} had the wrong token and was ignored", id);
                return;
            }
            if (op.getStatus().finished()) {
                return;   // Safaricom can send the same answer twice
            }
            if ("timeout".equals(kind)) {
                op.setStatus(BusinessOperationStatus.UNKNOWN);
                op.setFailure("Safaricom reported that the request timed out while waiting. Check your M-Pesa statement before trying again.");
                BusinessOperation saved = ops.save(op);
                notifier.text(op.getOwnerPhone(), "*No answer from Safaricom*\n\n" + saved.getSummary() + "\n\n" + saved.getFailure());
                return;
            }
            if (op.getType() == BusinessOperationType.STK_PUSH) {
                stkResult(op, payload == null ? Map.of() : payload);
            } else {
                initiatorResult(op, payload == null ? Map.of() : payload);
            }
        }
    }

    private void stkResult(BusinessOperation op, Map<String, Object> payload) {
        Map<String, Object> callback = map(map(payload.get("Body")).get("stkCallback"));
        String code = str(callback.get("ResultCode"));
        if (code == null) {
            return;
        }
        if (!"0".equals(code)) {
            finish(op, BusinessOperationStatus.FAILED, firstNonBlank(str(callback.get("ResultDesc")), "The customer did not complete the payment."), null, true);
            return;
        }
        String receipt = null;
        String paid = null;
        for (Map<String, Object> item : maps(map(callback.get("CallbackMetadata")).get("Item"))) {
            String name = str(item.get("Name"));
            if ("MpesaReceiptNumber".equals(name)) {
                receipt = str(item.get("Value"));
            } else if ("Amount".equals(name)) {
                paid = str(item.get("Value"));
            }
        }
        finish(op, BusinessOperationStatus.SUCCEEDED, "Paid" + (paid == null ? "" : " KES " + paid) + " by " + op.getParams().get("phone") + ".", receipt, true);
    }

    private void initiatorResult(BusinessOperation op, Map<String, Object> payload) {
        Map<String, Object> result = map(payload.get("Result"));
        String code = str(result.get("ResultCode"));
        if (code == null) {
            return;
        }
        String description = firstNonBlank(str(result.get("ResultDesc")), "Safaricom did not complete the request.");
        if (!"0".equals(code)) {
            finish(op, BusinessOperationStatus.FAILED, description + " (code " + code + ")", null, true);
            return;
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (Map<String, Object> pair : maps(map(result.get("ResultParameters")).get("ResultParameter"))) {
            String k = str(pair.get("Key"));
            String v = str(pair.get("Value"));
            if (k != null && v != null) {
                values.put(k, v);
            }
        }
        String receipt = firstNonBlank(str(result.get("TransactionID")), values.get("TransactionReceipt"));
        String text;
        switch (op.getType()) {
            case BALANCE -> text = balanceText(values.get("AccountBalance"));
            case STATUS -> text = values.isEmpty() ? description : values.entrySet().stream().limit(12)
                    .map(e -> e.getKey() + ": " + e.getValue()).collect(Collectors.joining("\n"));
            default -> {
                String who = values.get("ReceiverPartyPublicName");
                text = "Done" + (who == null ? "" : ". Received by " + who) + ".";
            }
        }
        finish(op, BusinessOperationStatus.SUCCEEDED, text, op.getType() == BusinessOperationType.BALANCE || op.getType() == BusinessOperationType.STATUS ? null : receipt, true);
    }

    // ------------------------------------------------------------------ ending an operation

    private BusinessOperation finish(BusinessOperation op, BusinessOperationStatus status, String text, String receipt, boolean tellChat) {
        op.setStatus(status);
        op.setResultText(trim(text, 2000));
        if (receipt != null) {
            op.setReceipt(trim(receipt, 30));
        }
        if (status == BusinessOperationStatus.FAILED) {
            op.setFailure(trim(text, 500));
        }
        op.setCompletedAt(clock.instant());
        BusinessOperation saved = ops.save(op);
        if (tellChat) {
            notifier.text(saved.getOwnerPhone(), chatText(saved));
        }
        try {
            finishedHook.finished(saved);
        } catch (RuntimeException e) {
            log.warn("A notice about a finished request failed: {}", e.getMessage());
        }
        return saved;
    }

    private BusinessOperation fail(BusinessOperation op, String reason, boolean tellChat) {
        return finish(op, BusinessOperationStatus.FAILED, reason == null || reason.isBlank() ? "The request failed." : reason, null, tellChat);
    }

    static String chatText(BusinessOperation op) {
        boolean ok = op.getStatus() == BusinessOperationStatus.SUCCEEDED;
        return "*" + op.getType().label() + (ok ? " done" : " failed") + "*\n\n" + op.getSummary()
                + "\n\n" + (op.getResultText() == null ? "" : op.getResultText())
                + (op.getReceipt() == null ? "" : "\n\n*M-Pesa code:* " + op.getReceipt());
    }

    private BusinessOperation expireIfStale(BusinessOperation op) {
        if (isStale(op)) {
            op.setStatus(BusinessOperationStatus.EXPIRED);
            op.setCompletedAt(clock.instant());
            op.setResultText("Not approved in time. Nothing was sent to Safaricom.");
            return ops.save(op);
        }
        return op;
    }

    private boolean isStale(BusinessOperation op) {
        return op.getStatus() == BusinessOperationStatus.PENDING_APPROVAL && op.getExpiresAt() != null && clock.instant().isAfter(op.getExpiresAt());
    }

    // ------------------------------------------------------------------ finding the account

    private BusinessAccountView findAccount(String owner, String ref) {
        List<BusinessAccountView> all = accounts.list(owner);
        if (all.isEmpty()) {
            throw new InvalidActionRequestException("You have no business accounts yet. Add one under Settings, Business accounts.");
        }
        if (blank(ref)) {
            if (all.size() == 1) {
                return all.get(0);
            }
            throw new InvalidActionRequestException("Which business account? You have: " + names(all) + ".");
        }
        String r = ref.trim().replaceFirst("^@", "").trim();
        try {
            UUID id = UUID.fromString(r);
            for (BusinessAccountView a : all) {
                if (a.id().equals(id)) {
                    return a;
                }
            }
        } catch (IllegalArgumentException ignored) {
            // a name, not an id
        }
        return pick(r, all.stream().filter(a -> a.label().equalsIgnoreCase(r)).toList(), all)
                .or(() -> pick(r, all.stream().filter(a -> r.equalsIgnoreCase(a.tillNumber()) || r.equalsIgnoreCase(a.accountReference())).toList(), all))
                .or(() -> pick(r, all.stream().filter(a -> r.equals(a.shortcode())).toList(), all))
                .or(() -> pick(r, all.stream().filter(a -> a.label().toLowerCase(Locale.ROOT).contains(r.toLowerCase(Locale.ROOT))).toList(), all))
                .orElseThrow(() -> new InvalidActionRequestException("No business account matches \"" + r + "\". You have: " + names(all) + "."));
    }

    /** One match is the answer. Several that share a shortcode resolve to the head account. Otherwise the person must be more exact. */
    private static Optional<BusinessAccountView> pick(String r, List<BusinessAccountView> found, List<BusinessAccountView> all) {
        if (found.isEmpty()) {
            return Optional.empty();
        }
        if (found.size() == 1) {
            return Optional.of(found.get(0));
        }
        List<BusinessAccountView> heads = found.stream().filter(a -> a.parentId() == null).toList();
        if (heads.size() == 1) {
            return Optional.of(heads.get(0));
        }
        throw new InvalidActionRequestException("More than one business account matches \"" + r + "\": " + names(found) + ". Use the full name.");
    }

    private static String names(List<BusinessAccountView> list) {
        return list.stream().map(a -> a.label() + " (" + a.environment().name().toLowerCase(Locale.ROOT) + ")").collect(Collectors.joining(", "));
    }

    private static void requireCapability(BusinessAccountView account, BusinessOperationType type) {
        for (BusinessAccountView.Capability c : account.capabilities()) {
            if (c.code().equals(type.capability())) {
                if (!c.ready()) {
                    throw new InvalidActionRequestException("\"" + account.label() + "\" cannot do " + type.label() + " yet. It needs the "
                            + c.needs() + ". Add it under Settings, Business accounts.");
                }
                return;
            }
        }
        throw new InvalidActionRequestException("\"" + account.label() + "\" cannot do " + type.label() + ".");
    }

    // ------------------------------------------------------------------ checking what was asked

    private Plan plan(String owner, BusinessOperationType type, BusinessAccountView account, Map<String, String> in) {
        Map<String, String> p = new LinkedHashMap<>();
        String on = " on " + account.label() + (account.tillNumber().isEmpty() ? "" : " (till " + account.tillNumber() + ")");
        switch (type) {
            case STK_PUSH -> {
                int amount = amount(in, true);
                String phone = phone(in, "phone");
                String reference = reference(in, account.label());
                p.put("phone", phone);
                p.put("reference", reference);
                p.put("description", trim(orElse(text(in, "description", 13), "Payment"), 13));
                return new Plan(amount, "STK Push of " + money(amount) + " to " + phone + " (reference " + reference + ")" + on, p);
            }
            case STK_QUERY -> {
                String checkout = text(in, "checkout_request_id", 64);
                String origin = text(in, "operation", 40);
                if (checkout.isEmpty() && origin.isEmpty()) {
                    throw new InvalidActionRequestException("Say which STK Push to check, by its operation id or checkout request id.");
                }
                if (checkout.isEmpty()) {
                    BusinessOperation o = findOperation(owner, origin);
                    if (o.getType() != BusinessOperationType.STK_PUSH || blank(o.getCheckoutRequestId())) {
                        throw new InvalidActionRequestException("That request is not an STK Push that Safaricom has accepted.");
                    }
                    checkout = o.getCheckoutRequestId();
                    p.put("operationId", o.getId().toString());
                }
                p.put("checkoutRequestId", checkout);
                return new Plan(0, "Check an STK Push" + on, p);
            }
            case DYNAMIC_QR -> {
                int amount = amount(in, true);
                String reference = reference(in, account.label());
                p.put("reference", reference);
                p.put("merchantName", trim(orElse(text(in, "merchant_name", 26), account.label()), 26));
                return new Plan(amount, "QR code for " + money(amount) + " (reference " + reference + ")" + on, p);
            }
            case PULL -> {
                LocalDateTime start = date(in, "start_date", false);
                LocalDateTime end = date(in, "end_date", true);
                if (!end.isAfter(start)) {
                    throw new InvalidActionRequestException("The end of the period must be after the start.");
                }
                String offset = text(in, "offset", 6);
                p.put("start", PULL_FORMAT.format(start));
                p.put("end", PULL_FORMAT.format(end));
                p.put("offset", offset.matches("\\d{1,6}") ? offset : "0");
                return new Plan(0, "Pull transactions from " + p.get("start") + " to " + p.get("end") + on, p);
            }
            case BALANCE -> {
                return new Plan(0, "Check the account balance" + on, p);
            }
            case STATUS -> {
                String receipt = receipt(in);
                p.put("receipt", receipt);
                return new Plan(0, "Check transaction " + receipt + on, p);
            }
            case B2C -> {
                int amount = amount(in, true);
                String phone = phone(in, "phone");
                String command = text(in, "command", 20);
                command = command.isEmpty() ? "BusinessPayment" : command;
                if (!B2C_COMMANDS.contains(command)) {
                    throw new InvalidActionRequestException("The payout type must be BusinessPayment, SalaryPayment or PromotionPayment.");
                }
                p.put("phone", phone);
                p.put("command", command);
                p.put("remarks", orElse(text(in, "remarks", 100), "Payment"));
                p.put("occasion", text(in, "occasion", 100));
                return new Plan(amount, "Pay " + money(amount) + " to " + phone + " from " + account.label() + (command.equals("BusinessPayment") ? "" : " (" + command + ")"), p);
            }
            case B2POCHI -> {
                int amount = amount(in, true);
                String phone = phone(in, "phone");
                p.put("phone", phone);
                p.put("remarks", orElse(text(in, "remarks", 100), "Payment"));
                p.put("occasion", text(in, "occasion", 100));
                return new Plan(amount, "Pay " + money(amount) + " to the Pochi la Biashara of " + phone + " from " + account.label(), p);
            }
            case B2B -> {
                int amount = amount(in, true);
                String receiver = text(in, "receiver", 12).replaceAll("\\D", "");
                if (!CODE.matcher(receiver).matches()) {
                    throw new InvalidActionRequestException("The receiving Paybill or Till number must be 5 to 7 digits.");
                }
                String target = text(in, "target_type", 10).toLowerCase(Locale.ROOT);
                boolean till = target.equals("till") || target.equals("buy_goods");
                if (!till && !target.equals("paybill") && !target.isEmpty()) {
                    throw new InvalidActionRequestException("The target must be a paybill or a till.");
                }
                String reference = text(in, "account_reference", 13);
                if (!till && reference.isEmpty()) {
                    throw new InvalidActionRequestException("A Paybill payment needs the account number to pay into.");
                }
                p.put("command", till ? "BusinessBuyGoods" : "BusinessPayBill");
                p.put("receiver", receiver);
                p.put("accountReference", till ? "" : reference);
                p.put("remarks", orElse(text(in, "remarks", 100), "Payment"));
                return new Plan(amount, "Pay " + money(amount) + " to " + (till ? "Till " : "Paybill ") + receiver + (till ? "" : ", account " + reference) + " from " + account.label(), p);
            }
            case REVERSAL -> {
                int amount = amount(in, true);
                String receipt = receipt(in);
                p.put("receipt", receipt);
                p.put("remarks", orElse(text(in, "remarks", 100), "Reversal"));
                return new Plan(amount, "Reverse transaction " + receipt + " (" + money(amount) + ")" + on, p);
            }
        }
        throw new InvalidActionRequestException("That kind of request is not supported.");
    }

    private int amount(Map<String, String> in, boolean required) {
        String raw = text(in, "amount", 12).replace(",", "");
        if (raw.isEmpty()) {
            if (required) {
                throw new InvalidActionRequestException("Say the amount in whole shillings.");
            }
            return 0;
        }
        int value;
        try {
            value = new BigDecimal(raw).intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new InvalidActionRequestException("The amount must be a whole number of shillings.");
        }
        if (value < 1) {
            throw new InvalidActionRequestException("The amount must be at least KES 1.");
        }
        if (value > props.maxAmount()) {
            throw new InvalidActionRequestException("The amount is above the limit of " + money(props.maxAmount()) + " for one request.");
        }
        return value;
    }

    private static String phone(Map<String, String> in, String key) {
        String raw = text(in, key, 20);
        if (raw.isEmpty()) {
            throw new InvalidActionRequestException("Say the phone number.");
        }
        return Phones.normalize(raw);
    }

    private static String reference(Map<String, String> in, String fallbackLabel) {
        String ref = text(in, "reference", 20);
        if (ref.isEmpty()) {
            String cleaned = fallbackLabel.replaceAll("[^A-Za-z0-9]", "");
            ref = cleaned.isEmpty() ? "Payment" : trim(cleaned, 12);
        }
        if (!REFERENCE.matcher(ref).matches()) {
            throw new InvalidActionRequestException("The reference can only have letters and numbers, up to 12 characters.");
        }
        return ref;
    }

    private static String receipt(Map<String, String> in) {
        String receipt = text(in, "receipt", 20).toUpperCase(Locale.ROOT);
        if (!RECEIPT.matcher(receipt).matches()) {
            throw new InvalidActionRequestException("The M-Pesa transaction code should be 8 to 12 letters and digits, like QGH7ABC123.");
        }
        return receipt;
    }

    private static LocalDateTime date(Map<String, String> in, String key, boolean endOfDay) {
        String raw = text(in, key, 20);
        if (raw.isEmpty()) {
            throw new InvalidActionRequestException("Say the " + (endOfDay ? "end" : "start") + " of the period, like 2026-11-01.");
        }
        try {
            if (raw.length() <= 10) {
                return LocalDate.parse(raw).atTime(endOfDay ? LocalTime.of(23, 59) : LocalTime.MIDNIGHT);
            }
            return LocalDateTime.parse(raw.replace(' ', 'T'));
        } catch (DateTimeParseException e) {
            throw new InvalidActionRequestException("Write the date like 2026-11-01 or 2026-11-01 08:30.");
        }
    }

    private BusinessOperation findOperation(String owner, String ref) {
        try {
            return require(owner, UUID.fromString(ref));
        } catch (IllegalArgumentException e) {
            String prefix = ref.toLowerCase(Locale.ROOT);
            List<BusinessOperation> hits = ops.findTop30ByOwnerPhoneOrderByCreatedAtDesc(owner).stream()
                    .filter(o -> o.getId().toString().startsWith(prefix)).toList();
            if (hits.size() == 1) {
                return hits.get(0);
            }
            throw new InvalidActionRequestException("No request with that id was found.");
        }
    }

    // ------------------------------------------------------------------ parsing Safaricom's JSON

    private static String pullText(Map<String, Object> answer) {
        List<Map<String, Object>> rows = maps(answer.get("Response"));
        String message = firstNonBlank(str(answer.get("ResponseMessage")), str(answer.get("ResponseDescription")), "");
        if (rows.isEmpty()) {
            return message.isEmpty() ? "No transactions were returned for that period." : message;
        }
        String lines = rows.stream().limit(10).map(r -> "- " + firstNonBlank(str(r.get("transactionId")), "?") + ": KES "
                + firstNonBlank(str(r.get("amount")), "?") + " on " + firstNonBlank(str(r.get("trxDate")), "?")
                + (blank(str(r.get("billreference"))) ? "" : " (ref " + str(r.get("billreference")) + ")")).collect(Collectors.joining("\n"));
        return rows.size() + " transaction" + (rows.size() == 1 ? "" : "s") + (rows.size() > 10 ? ", the first 10:" : ":") + "\n" + lines;
    }

    /**
     * Turns "Working Account|KES|700000.00|700000.00|0.00|0.00&Utility Account|KES|..." into one line per account.
     * The fourth value is the available balance.
     */
    static String balanceText(String raw) {
        if (blank(raw)) {
            return "Safaricom did not return the balance.";
        }
        List<String> lines = new ArrayList<>();
        for (String account : raw.split("&")) {
            String[] parts = account.split("\\|");
            if (parts.length < 3) {
                continue;
            }
            String amount = parts.length > 3 ? parts[3].trim() : parts[2].trim();
            try {
                lines.add(parts[0].trim() + ": " + parts[1].trim() + " " + String.format(Locale.ENGLISH, "%,.2f", new BigDecimal(amount)));
            } catch (NumberFormatException e) {
                lines.add(parts[0].trim() + ": " + parts[1].trim() + " " + amount);
            }
        }
        return lines.isEmpty() ? raw : String.join("\n", lines);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    /** A list of objects. Safaricom sends a single object instead of a list when there is only one. */
    private static List<Map<String, Object>> maps(Object o) {
        if (o instanceof Map<?, ?>) {
            return List.of(map(o));
        }
        if (o instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?>) {
                    out.add(map(item));
                }
            }
            return out;
        }
        return List.of();
    }

    // ------------------------------------------------------------------ small helpers

    private BusinessOperation require(String owner, UUID id) {
        return ops.findByIdAndOwnerPhone(id, owner).orElseThrow(() -> new ActionNotFoundException("That request was not found."));
    }

    private Object lockFor(UUID id) {
        return locks.computeIfAbsent(id, k -> new Object());
    }

    private static String newToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String text(Map<String, String> in, String key, int max) {
        String v = in.get(key);
        if (v == null) {
            return "";
        }
        v = v.trim();
        return v.length() > max ? v.substring(0, max) : v;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static String orElse(String s, String fallback) {
        return blank(s) ? fallback : s;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (!blank(v)) {
                return v;
            }
        }
        return null;
    }

    private static String money(int amount) {
        return String.format(Locale.ENGLISH, "KES %,d", amount);
    }
}
