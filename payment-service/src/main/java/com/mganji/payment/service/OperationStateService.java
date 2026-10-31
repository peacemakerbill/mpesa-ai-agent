package com.mganji.payment.service;

import com.mganji.payment.config.OperationsProperties;
import com.mganji.payment.dto.DarajaResultPayload;
import com.mganji.payment.event.ApprovalRequestedEvent;
import com.mganji.payment.event.OperationCompletedEvent;
import com.mganji.payment.exception.ForbiddenOperationException;
import com.mganji.payment.exception.InvalidOperationStateException;
import com.mganji.payment.exception.PaymentNotFoundException;
import com.mganji.payment.model.MpesaOperation;
import com.mganji.payment.model.OperationStatus;
import com.mganji.payment.model.OperationType;
import com.mganji.payment.repository.MpesaOperationRepository;
import com.mganji.payment.util.BalanceParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The only place where an operation changes status. Same idea as PaymentStateService: short transactions,
 * row locks, events published after commit. Nothing here talks to Daraja.
 */
@Service
public class OperationStateService {

    private static final Logger log = LoggerFactory.getLogger(OperationStateService.class);

    private final MpesaOperationRepository repository;
    private final ApplicationEventPublisher events;
    private final StaffService staff;
    private final OperationsProperties limits;
    private final ObjectMapper objectMapper;

    public OperationStateService(MpesaOperationRepository repository,
                                 ApplicationEventPublisher events,
                                 StaffService staff,
                                 OperationsProperties limits,
                                 ObjectMapper objectMapper) {
        this.repository = repository;
        this.events = events;
        this.staff = staff;
        this.limits = limits;
        this.objectMapper = objectMapper;
    }

    /** Tell the approvers that something is waiting for them. */
    @Transactional
    public MpesaOperation announceApproval(UUID id) {
        MpesaOperation op = lock(id);
        events.publishEvent(ApprovalRequestedEvent.from(op, staff.approverPhones()));
        return op;
    }

    /** Maker checker: someone other than the requester must approve, unless self approval is switched on. */
    @Transactional
    public MpesaOperation approve(UUID id, String approverPhone) {
        MpesaOperation op = lock(id);
        if (op.getStatus() != OperationStatus.PENDING_APPROVAL) {
            throw new InvalidOperationStateException("This request is " + op.getStatus() + " and cannot be approved");
        }
        if (!limits.allowSelfApproval() && op.getRequestedBy().equals(approverPhone)) {
            throw new ForbiddenOperationException("You cannot approve a request you made yourself");
        }
        op.setApprovedBy(approverPhone);
        op.setStatus(OperationStatus.CREATED);
        log.info("Operation {} ({}) approved", id, op.getType());
        return repository.save(op);
    }

    @Transactional
    public MpesaOperation reject(UUID id, String actorPhone, String reason) {
        MpesaOperation op = lock(id);
        if (op.getStatus() != OperationStatus.PENDING_APPROVAL) {
            throw new InvalidOperationStateException("This request is " + op.getStatus() + " and cannot be rejected");
        }
        op.setApprovedBy(actorPhone);
        op.setStatus(OperationStatus.REJECTED);
        op.setResultDesc(abbreviate(reason == null || reason.isBlank() ? "Rejected by approver" : reason, 255));
        repository.save(op);
        log.info("Operation {} ({}) rejected", id, op.getType());
        events.publishEvent(OperationCompletedEvent.from(op, Map.of()));
        return op;
    }

    /** Assigns the id Safaricom will echo back, and marks the operation as sent, before calling Daraja. */
    @Transactional
    public MpesaOperation prepareSubmit(UUID id) {
        MpesaOperation op = lock(id);
        if (op.getStatus() != OperationStatus.CREATED) {
            throw new InvalidOperationStateException("This request is " + op.getStatus() + " and cannot be sent");
        }
        op.setOriginatorConversationId(UUID.randomUUID().toString());
        op.setStatus(OperationStatus.SUBMITTED);
        return repository.save(op);
    }

    @Transactional
    public MpesaOperation recordConversation(UUID id, String conversationId) {
        MpesaOperation op = lock(id);
        op.setConversationId(abbreviate(conversationId, 60));
        return repository.save(op);
    }

    @Transactional
    public MpesaOperation markFailed(UUID id, String code, String description) {
        MpesaOperation op = lock(id);
        if (!op.getStatus().isFinal()) {
            op.setStatus(OperationStatus.FAILED);
            op.setResultCode(abbreviate(code, 20));
            op.setResultDesc(abbreviate(description, 255));
            repository.save(op);
            log.warn("Operation {} ({}) failed: {} {}", id, op.getType(), code, description);
            events.publishEvent(OperationCompletedEvent.from(op, Map.of()));
        }
        return op;
    }

    /** We could not tell whether Daraja got it. It stays SUBMITTED because a result callback may still arrive. */
    @Transactional
    public MpesaOperation noteUncertain(UUID id, String note) {
        MpesaOperation op = lock(id);
        if (op.getStatus() == OperationStatus.SUBMITTED) {
            op.setResultDesc(abbreviate(note, 255));
            repository.save(op);
        }
        return op;
    }

    /** Applies Safaricom's result (or queue timeout) to the operation it belongs to. */
    @Transactional
    public Optional<MpesaOperation> applyResult(DarajaResultPayload.Result result, boolean timedOut) {
        MpesaOperation op = findForUpdate(result);
        if (op == null) {
            log.warn("Result for unknown conversation {} / {}", result.originatorConversationId(), result.conversationId());
            return Optional.empty();
        }
        if (op.getStatus().isFinal()) {
            log.info("Ignoring duplicate result for operation {}, already {}", op.getId(), op.getStatus());
            return Optional.empty();
        }

        if (timedOut) {
            if (op.getStatus() != OperationStatus.SUBMITTED) {
                return Optional.empty();
            }
            op.setStatus(OperationStatus.TIMEOUT);
            op.setResultCode(abbreviate(result.resultCode(), 20));
            op.setResultDesc("M-Pesa did not answer in time. Check the statement before trying again.");
            repository.save(op);
            events.publishEvent(OperationCompletedEvent.from(op, Map.of()));
            return Optional.of(op);
        }

        boolean success = "0".equals(result.resultCode().trim());
        Map<String, String> params = result.parameters();
        Map<String, String> details = op.getType() == OperationType.ACCOUNT_BALANCE && params.containsKey("AccountBalance")
                ? BalanceParser.parse(params.get("AccountBalance"))
                : params;

        op.setStatus(success ? OperationStatus.SUCCESS : OperationStatus.FAILED);
        op.setResultCode(abbreviate(result.resultCode(), 20));
        op.setResultDesc(abbreviate(result.resultDesc(), 255));
        op.setMpesaReceipt(abbreviate(firstNonBlank(
                params.get("TransactionReceipt"), params.get("ReceiptNo"), result.transactionId()), 30));
        op.setResultData(toJson(details));
        repository.save(op);

        log.info("Operation {} ({}) is now {}", op.getId(), op.getType(), op.getStatus());
        events.publishEvent(OperationCompletedEvent.from(op, details));
        return Optional.of(op);
    }

    private MpesaOperation findForUpdate(DarajaResultPayload.Result result) {
        if (result.originatorConversationId() != null && !result.originatorConversationId().isBlank()) {
            Optional<MpesaOperation> byOriginator =
                    repository.findByOriginatorConversationIdForUpdate(result.originatorConversationId());
            if (byOriginator.isPresent()) {
                return byOriginator.get();
            }
        }
        if (result.conversationId() != null && !result.conversationId().isBlank()) {
            return repository.findByConversationIdForUpdate(result.conversationId()).orElse(null);
        }
        return null;
    }

    private MpesaOperation lock(UUID id) {
        return repository.findByIdForUpdate(id)
                .orElseThrow(() -> new PaymentNotFoundException("Operation not found: " + id));
    }

    private String toJson(Map<String, String> map) {
        try {
            return abbreviate(objectMapper.writeValueAsString(map), 4000);
        } catch (JacksonException e) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String abbreviate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}