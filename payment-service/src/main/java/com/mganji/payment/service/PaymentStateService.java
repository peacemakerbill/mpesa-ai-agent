package com.mganji.payment.service;

import com.mganji.payment.dto.StkResult;
import com.mganji.payment.event.PaymentCompletedEvent;
import com.mganji.payment.exception.PaymentNotFoundException;
import com.mganji.payment.model.Payment;
import com.mganji.payment.model.PaymentStatus;
import com.mganji.payment.repository.PaymentRepository;
import com.mganji.payment.util.PhoneNumberUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * The only place where a payment changes status. Every transition runs in its own short transaction
 * with a row lock, so a callback, a retry and the reconciler can never overwrite each other.
 * M-Pesa results are the only thing that can make a payment SUCCESS.
 */
@Service
public class PaymentStateService {

    private static final Logger log = LoggerFactory.getLogger(PaymentStateService.class);

    private final PaymentRepository repository;
    private final ApplicationEventPublisher events;

    public PaymentStateService(PaymentRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    /** Daraja accepted the STK push, the customer now has the PIN prompt. */
    @Transactional
    public Payment markPushed(UUID id, String merchantRequestId, String checkoutRequestId) {
        Payment payment = lock(id);
        if (payment.getStatus() == PaymentStatus.PENDING) {
            payment.setMerchantRequestId(merchantRequestId);
            payment.setCheckoutRequestId(checkoutRequestId);
            payment.setStatus(PaymentStatus.PUSHED);
            payment.setResultDesc("Waiting for the customer to enter their M-Pesa PIN");
            repository.save(payment);
        }
        return payment;
    }

    /** Daraja clearly refused the request, so no prompt was sent. */
    @Transactional
    public Payment markPushFailed(UUID id, String code, String description) {
        Payment payment = lock(id);
        if (payment.getStatus() == PaymentStatus.PENDING) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setResultCode(abbreviate(code, 20));
            payment.setResultDesc(abbreviate(description, 255));
            repository.save(payment);
            log.info("Payment {} failed at initiation: {} {}", id, code, description);
        }
        return payment;
    }

    /**
     * We could not tell whether Daraja received the request. The payment stays PENDING (never FAILED)
     * so nobody is told to try again while a prompt may already be on their phone.
     */
    @Transactional
    public Payment markUncertain(UUID id, String note) {
        Payment payment = lock(id);
        if (payment.getStatus() == PaymentStatus.PENDING) {
            payment.setResultDesc(abbreviate(note, 255));
            repository.save(payment);
        }
        return payment;
    }

    /**
     * Applies a final outcome from a Safaricom callback or an STK query.
     *
     * @return the updated payment, or empty when the result was unknown, a duplicate, or changed nothing
     */
    @Transactional
    public Optional<Payment> applyResult(StkResult result, String source) {
        Payment payment = repository.findByCheckoutRequestIdForUpdate(result.checkoutRequestId()).orElse(null);
        if (payment == null) {
            log.warn("Received {} result for unknown checkoutRequestId {}", source, result.checkoutRequestId());
            return Optional.empty();
        }
        if (payment.getStatus().isFinal()) {
            log.info("Ignoring duplicate {} result for payment {}, already {}", source, payment.getId(), payment.getStatus());
            return Optional.empty();
        }

        PaymentStatus target = PaymentStatus.fromResultCode(result.resultCode());
        if (target == payment.getStatus()) {
            return Optional.empty();
        }
        if (target == PaymentStatus.SUCCESS && result.amount() != null && result.amount() != payment.getAmount()) {
            log.error("Amount mismatch on payment {}: expected {} but M-Pesa reports {}",
                    payment.getId(), payment.getAmount(), result.amount());
        }

        payment.setStatus(target);
        payment.setResultCode(abbreviate(result.resultCode(), 20));
        payment.setResultDesc(abbreviate(result.resultDesc(), 255));
        if (target == PaymentStatus.SUCCESS) {
            payment.setMpesaReceipt(abbreviate(result.receipt(), 30));
        }
        repository.save(payment);

        log.info("Payment {} is now {} (source={}, resultCode={}, phone={})",
                payment.getId(), target, source, result.resultCode(), PhoneNumberUtil.mask(payment.getPhone()));
        events.publishEvent(PaymentCompletedEvent.from(payment));
        return Optional.of(payment);
    }

    /** No answer from the customer or from Daraja within the allowed time. */
    @Transactional
    public Optional<Payment> markTimeout(UUID id) {
        Payment payment = lock(id);
        if (payment.getStatus() != PaymentStatus.PUSHED) {
            return Optional.empty();
        }
        payment.setStatus(PaymentStatus.TIMEOUT);
        payment.setResultDesc("No response received from M-Pesa in time");
        repository.save(payment);
        log.warn("Payment {} timed out waiting for M-Pesa", id);
        events.publishEvent(PaymentCompletedEvent.from(payment));
        return Optional.of(payment);
    }

    private Payment lock(UUID id) {
        return repository.findByIdForUpdate(id)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + id));
    }

    private static String abbreviate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
