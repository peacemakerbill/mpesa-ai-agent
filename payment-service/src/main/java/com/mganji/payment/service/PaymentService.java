package com.mganji.payment.service;

import com.mganji.payment.config.LimitsProperties;
import com.mganji.payment.dto.CreatePaymentRequest;
import com.mganji.payment.dto.StkPushResponse;
import com.mganji.payment.exception.IdempotencyConflictException;
import com.mganji.payment.exception.InvalidPaymentRequestException;
import com.mganji.payment.exception.LimitExceededException;
import com.mganji.payment.exception.MpesaApiException;
import com.mganji.payment.exception.MpesaCommunicationException;
import com.mganji.payment.exception.PaymentNotFoundException;
import com.mganji.payment.model.Payment;
import com.mganji.payment.model.PaymentStatus;
import com.mganji.payment.repository.PaymentRepository;
import com.mganji.payment.util.PhoneNumberUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Starts payments. It is deliberately not @Transactional: the call to Daraja is slow and must never
 * hold a database transaction open. Each state change is its own small transaction in PaymentStateService.
 *
 * Known limit: the daily limit check and the insert are not atomic, so two simultaneous requests for the
 * same phone with different keys could exceed the daily limit slightly. Acceptable for a pilot, add a
 * per phone advisory lock before raising limits.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    /** Statuses that count towards the daily limit. */
    private static final Set<PaymentStatus> COUNTED =
            EnumSet.of(PaymentStatus.PENDING, PaymentStatus.PUSHED, PaymentStatus.SUCCESS);

    private final PaymentRepository repository;
    private final PaymentStateService state;
    private final MpesaClient mpesa;
    private final LimitsProperties limits;

    public PaymentService(PaymentRepository repository,
                          PaymentStateService state,
                          MpesaClient mpesa,
                          LimitsProperties limits) {
        this.repository = repository;
        this.state = state;
        this.mpesa = mpesa;
        this.limits = limits;
    }

    public Payment initiate(CreatePaymentRequest request) {
        String phone = PhoneNumberUtil.normalize(request.phone());
        int amount = request.amount();
        if (amount < 1) {
            throw new InvalidPaymentRequestException("Amount must be at least KES 1");
        }
        String reference = request.accountReference();

        Optional<Payment> existing = repository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            return replay(existing.get(), phone, amount, reference);
        }

        enforceLimits(phone, amount);

        Payment payment = new Payment(request.idempotencyKey(), phone, amount, reference);
        try {
            payment = repository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            // Two identical requests raced. The first one wins, we return its payment.
            Payment winner = repository.findByIdempotencyKey(request.idempotencyKey()).orElseThrow(() -> e);
            return replay(winner, phone, amount, reference);
        }
        return pushToMpesa(payment);
    }

    public Payment get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + id));
    }

    /** Returns the payment only if it belongs to this phone. A wrong owner looks exactly like a missing payment. */
    public Payment get(UUID id, String rawPhone) {
        String phone = PhoneNumberUtil.normalize(rawPhone);
        Payment payment = get(id);
        if (!payment.getPhone().equals(phone)) {
            throw new PaymentNotFoundException("Payment not found: " + id);
        }
        return payment;
    }

    /** Newest first, at most 20. */
    public List<Payment> list(String rawPhone, int limit) {
        String phone = PhoneNumberUtil.normalize(rawPhone);
        int size = Math.max(1, Math.min(limit, 20));
        return repository.findByPhoneOrderByCreatedAtDesc(phone, PageRequest.of(0, size));
    }

    public Payment latestForPhone(String rawPhone) {
        String phone = PhoneNumberUtil.normalize(rawPhone);
        return repository.findFirstByPhoneOrderByCreatedAtDesc(phone)
                .orElseThrow(() -> new PaymentNotFoundException("No payments found for this phone number"));
    }

    private Payment replay(Payment existing, String phone, int amount, String reference) {
        boolean sameRequest = existing.getPhone().equals(phone)
                && existing.getAmount() == amount
                && existing.getAccountReference().equals(reference);
        if (!sameRequest) {
            throw new IdempotencyConflictException("Idempotency key was already used for a different payment");
        }
        log.info("Idempotent replay for payment {} ({})", existing.getId(), existing.getStatus());
        return existing;
    }

    private void enforceLimits(String phone, int amount) {
        if (amount > limits.perTransaction()) {
            throw new LimitExceededException("Amount is above the per-transaction limit of KES " + limits.perTransaction());
        }
        Long used = repository.sumAmountSince(phone, COUNTED, Instant.now().minus(Duration.ofHours(24)));
        long total = (used == null ? 0L : used) + amount;
        if (total > limits.perDay()) {
            throw new LimitExceededException("This payment would exceed the daily limit of KES " + limits.perDay());
        }
    }

    private Payment pushToMpesa(Payment payment) {
        UUID id = payment.getId();
        try {
            StkPushResponse response = mpesa.stkPush(payment.getPhone(), payment.getAmount(), payment.getAccountReference());
            if (response.accepted()) {
                log.info("STK push accepted for payment {} phone={}", id, PhoneNumberUtil.mask(payment.getPhone()));
                return state.markPushed(id, response.merchantRequestId(), response.checkoutRequestId());
            }
            return state.markPushFailed(id, response.responseCode(), response.responseDescription());
        } catch (MpesaApiException e) {
            return state.markPushFailed(id, e.getCode(), e.getMessage());
        } catch (MpesaCommunicationException e) {
            log.error("Outcome of STK push for payment {} is unknown: {}", id, e.getMessage());
            return state.markUncertain(id,
                    "Could not confirm the request with M-Pesa. Do not retry, the customer may receive a prompt.");
        }
    }
}
