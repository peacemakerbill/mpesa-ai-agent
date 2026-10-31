package com.mganji.payment.service;

import com.mganji.payment.config.OperationsProperties;
import com.mganji.payment.dto.CreateOperationRequest;
import com.mganji.payment.dto.DarajaAsyncResponse;
import com.mganji.payment.exception.ForbiddenOperationException;
import com.mganji.payment.exception.IdempotencyConflictException;
import com.mganji.payment.exception.InvalidPaymentRequestException;
import com.mganji.payment.exception.LimitExceededException;
import com.mganji.payment.exception.MpesaApiException;
import com.mganji.payment.exception.MpesaCommunicationException;
import com.mganji.payment.exception.OperationNotConfiguredException;
import com.mganji.payment.exception.PaymentNotFoundException;
import com.mganji.payment.model.MpesaOperation;
import com.mganji.payment.model.OperationStatus;
import com.mganji.payment.model.OperationType;
import com.mganji.payment.model.Payment;
import com.mganji.payment.model.PaymentStatus;
import com.mganji.payment.model.StaffRole;
import com.mganji.payment.repository.MpesaOperationRepository;
import com.mganji.payment.repository.PaymentRepository;
import com.mganji.payment.util.PhoneNumberUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Runs every M-Pesa operation other than collecting a payment: refunds, payouts, reversals, balance and
 * transaction lookups. Who may do what is decided here from the phone number, never by the caller.
 *
 * Rules: customers can only ask for a refund of their own successful payment. Staff can look up
 * transactions and request payouts. Admins can also read the balance and request reversals. Money going
 * out always waits for an approver who is not the requester. Like PaymentService, this class is not
 * transactional because Daraja calls must not hold a database transaction open.
 */
@Service
public class OperationService {

    private static final Logger log = LoggerFactory.getLogger(OperationService.class);

    private static final Set<OperationType> B2C_TYPES =
            EnumSet.of(OperationType.REFUND, OperationType.WITHDRAWAL, OperationType.DISBURSEMENT);
    private static final Set<OperationStatus> COMMITTED_STATUSES = EnumSet.of(
            OperationStatus.PENDING_APPROVAL, OperationStatus.CREATED, OperationStatus.SUBMITTED, OperationStatus.SUCCESS);

    private final MpesaOperationRepository repository;
    private final OperationStateService state;
    private final MpesaOperationsClient client;
    private final StaffService staff;
    private final PaymentRepository payments;
    private final OperationsProperties limits;

    public OperationService(MpesaOperationRepository repository,
                            OperationStateService state,
                            MpesaOperationsClient client,
                            StaffService staff,
                            PaymentRepository payments,
                            OperationsProperties limits) {
        this.repository = repository;
        this.state = state;
        this.client = client;
        this.staff = staff;
        this.payments = payments;
        this.limits = limits;
    }

    public MpesaOperation request(CreateOperationRequest request) {
        String actor = PhoneNumberUtil.normalize(request.actorPhone());
        Optional<StaffRole> role = staff.roleOf(actor);

        Optional<MpesaOperation> existing = repository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            return replay(existing.get(), actor, request);
        }

        MpesaOperation op = build(request, actor, role);
        enforcePayoutLimits(op);
        client.requireInitiator();

        try {
            op = repository.saveAndFlush(op);
        } catch (DataIntegrityViolationException e) {
            MpesaOperation winner = repository.findByIdempotencyKey(request.idempotencyKey()).orElseThrow(() -> e);
            return replay(winner, actor, request);
        }
        log.info("Operation {} ({}) requested by {}", op.getId(), op.getType(), PhoneNumberUtil.mask(actor));

        if (op.getStatus() == OperationStatus.PENDING_APPROVAL) {
            return state.announceApproval(op.getId());
        }
        return submit(op.getId());
    }

    public MpesaOperation approve(UUID id, String actorPhone) {
        String actor = PhoneNumberUtil.normalize(actorPhone);
        requireRole(staff.roleOf(actor), StaffRole.APPROVER);
        MpesaOperation approved = state.approve(id, actor);
        return submit(approved.getId());
    }

    public MpesaOperation reject(UUID id, String actorPhone, String reason) {
        String actor = PhoneNumberUtil.normalize(actorPhone);
        requireRole(staff.roleOf(actor), StaffRole.APPROVER);
        return state.reject(id, actor, reason);
    }

    /** Customers see only their own requests. Staff see any. */
    public MpesaOperation get(UUID id, String actorPhone) {
        String actor = PhoneNumberUtil.normalize(actorPhone);
        MpesaOperation op = repository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException("Operation not found: " + id));
        if (!op.getRequestedBy().equals(actor) && staff.roleOf(actor).isEmpty()) {
            throw new PaymentNotFoundException("Operation not found: " + id);
        }
        return op;
    }

    /** Requests waiting for approval. Approvers only. */
    public List<MpesaOperation> pendingApprovals(String actorPhone) {
        String actor = PhoneNumberUtil.normalize(actorPhone);
        requireRole(staff.roleOf(actor), StaffRole.APPROVER);
        return repository.findTop20ByStatusOrderByCreatedAtAsc(OperationStatus.PENDING_APPROVAL);
    }

    public List<MpesaOperation> recent(String actorPhone) {
        return repository.findTop20ByRequestedByOrderByCreatedAtDesc(PhoneNumberUtil.normalize(actorPhone));
    }

    private MpesaOperation build(CreateOperationRequest req, String actor, Optional<StaffRole> role) {
        OperationType type = req.type();
        MpesaOperation op = new MpesaOperation(req.idempotencyKey(), type, actor);
        op.setReason(abbreviate(req.reason(), 255));

        switch (type) {
            case REFUND -> {
                if (req.relatedPaymentId() == null) {
                    throw new InvalidPaymentRequestException("relatedPaymentId is required for a refund");
                }
                Payment payment = payments.findById(req.relatedPaymentId())
                        .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + req.relatedPaymentId()));
                if (role.isEmpty() && !payment.getPhone().equals(actor)) {
                    throw new ForbiddenOperationException("You can only request a refund for your own payments");
                }
                if (payment.getStatus() != PaymentStatus.SUCCESS) {
                    throw new InvalidPaymentRequestException("Only successful payments can be refunded");
                }
                long alreadyRefunded = nullToZero(repository.sumForPayment(
                        payment.getId(), OperationType.REFUND, COMMITTED_STATUSES));
                long remaining = payment.getAmount() - alreadyRefunded;
                int amount = req.amount() == null ? (int) remaining : req.amount();
                if (remaining < 1 || amount < 1 || amount > remaining) {
                    throw new InvalidPaymentRequestException(
                            "Refund amount must be between 1 and KES " + Math.max(remaining, 0) + " (what is left to refund)");
                }
                op.setRelatedPaymentId(payment.getId());
                op.setRecipientPhone(payment.getPhone());   // refunds always go back to whoever paid
                op.setAmount(amount);
            }
            case WITHDRAWAL, DISBURSEMENT -> {
                requireRole(role, StaffRole.STAFF);
                if (req.recipientPhone() == null || req.amount() == null) {
                    throw new InvalidPaymentRequestException("recipientPhone and amount are required for a payout");
                }
                op.setRecipientPhone(PhoneNumberUtil.normalize(req.recipientPhone()));
                op.setAmount(req.amount());
            }
            case REVERSAL -> {
                requireRole(role, StaffRole.ADMIN);
                if (req.targetReceipt() == null || req.targetReceipt().isBlank() || req.amount() == null) {
                    throw new InvalidPaymentRequestException("targetReceipt and amount are required for a reversal");
                }
                op.setTargetReceipt(req.targetReceipt().toUpperCase());
                op.setAmount(req.amount());
            }
            case TRANSACTION_STATUS -> {
                requireRole(role, StaffRole.STAFF);
                if (req.targetReceipt() == null || req.targetReceipt().isBlank()) {
                    throw new InvalidPaymentRequestException("targetReceipt is required for a transaction lookup");
                }
                op.setTargetReceipt(req.targetReceipt().toUpperCase());
            }
            case ACCOUNT_BALANCE -> requireRole(role, StaffRole.ADMIN);
        }
        return op;
    }

    private void enforcePayoutLimits(MpesaOperation op) {
        if (!B2C_TYPES.contains(op.getType())) {
            return;
        }
        int amount = op.getAmount();
        if (amount > limits.maxPayout()) {
            throw new LimitExceededException("A single payout cannot exceed KES " + limits.maxPayout());
        }
        long committed = nullToZero(repository.sumAmountSince(
                B2C_TYPES, COMMITTED_STATUSES, Instant.now().minus(Duration.ofHours(24))));
        if (committed + amount > limits.dailyPayoutLimit()) {
            throw new LimitExceededException("This would exceed the daily payout limit of KES " + limits.dailyPayoutLimit());
        }
    }

    /** Sends an approved or ready operation to Daraja. The result arrives later on the callback URL. */
    private MpesaOperation submit(UUID id) {
        MpesaOperation op = state.prepareSubmit(id);
        try {
            DarajaAsyncResponse response = dispatch(op);
            if (response.accepted()) {
                return state.recordConversation(id, response.conversationId());
            }
            return state.markFailed(id, response.responseCode(), response.responseDescription());
        } catch (MpesaApiException e) {
            return state.markFailed(id, e.getCode(), e.getMessage());
        } catch (OperationNotConfiguredException e) {
            return state.markFailed(id, "NOT_CONFIGURED", e.getMessage());
        } catch (MpesaCommunicationException e) {
            log.error("Outcome of operation {} is unknown: {}", id, e.getMessage());
            return state.noteUncertain(id,
                    "Could not confirm the request with M-Pesa. Check the M-Pesa statement before retrying.");
        }
    }

    private DarajaAsyncResponse dispatch(MpesaOperation op) {
        String remarks = op.getReason() == null || op.getReason().isBlank() ? op.getType().name() : op.getReason();
        return switch (op.getType()) {
            case REFUND, WITHDRAWAL, DISBURSEMENT -> client.b2c(
                    op.getOriginatorConversationId(), op.getAmount(), op.getRecipientPhone(),
                    remarks, op.getId().toString());
            case REVERSAL -> client.reversal(op.getTargetReceipt(), op.getAmount(), remarks);
            case TRANSACTION_STATUS -> client.transactionStatus(op.getTargetReceipt(), remarks);
            case ACCOUNT_BALANCE -> client.accountBalance(remarks);
        };
    }

    private MpesaOperation replay(MpesaOperation existing, String actor, CreateOperationRequest req) {
        boolean same = existing.getType() == req.type()
                && existing.getRequestedBy().equals(actor)
                && (req.amount() == null || existing.getAmount() == null || existing.getAmount().equals(req.amount()));
        if (!same) {
            throw new IdempotencyConflictException("Idempotency key was already used for a different request");
        }
        log.info("Idempotent replay for operation {} ({})", existing.getId(), existing.getStatus());
        return existing;
    }

    private static void requireRole(Optional<StaffRole> role, StaffRole minimum) {
        if (role.isEmpty() || !role.get().atLeast(minimum)) {
            throw new ForbiddenOperationException("You do not have permission to do this");
        }
    }

    private static long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private static String abbreviate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
