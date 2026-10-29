package com.mpesaagent.payment.repository;

import com.mpesaagent.payment.config.JpaRepositoriesConfig;
import com.mpesaagent.payment.model.Payment;
import com.mpesaagent.payment.model.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the real Flyway migration against H2 and lets Hibernate validate the entity against it,
 * so a mismatch between V1__create_payments.sql and the Payment entity fails here.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaRepositoriesConfig.class)
class PaymentRepositoryTest {

    private static final Set<PaymentStatus> COUNTED =
            EnumSet.of(PaymentStatus.PENDING, PaymentStatus.PUSHED, PaymentStatus.SUCCESS);

    @Autowired
    PaymentRepository repository;

    private Payment save(String phone, int amount, PaymentStatus status, Instant createdAt) {
        Payment p = new Payment(UUID.randomUUID().toString(), phone, amount, "ORD-1");
        p.setStatus(status);
        if (createdAt != null) {
            ReflectionTestUtils.setField(p, "createdAt", createdAt);
        }
        return repository.saveAndFlush(p);
    }

    @Test
    void savesAndFindsByIdempotencyKey() {
        Payment saved = save("254700000101", 100, PaymentStatus.PENDING, null);

        Payment found = repository.findByIdempotencyKey(saved.getIdempotencyKey()).orElseThrow();

        assertThat(found.getId()).isNotNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
        assertThat(found.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void duplicateIdempotencyKeyViolatesUniqueConstraint() {
        Payment first = save("254700000102", 100, PaymentStatus.PENDING, null);

        assertThatThrownBy(() -> repository.saveAndFlush(new Payment(first.getIdempotencyKey(), "254700000102", 100, "ORD-1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void dailySumCountsOnlyActiveStatusesInsideTheWindow() {
        String phone = "254700000103";
        Instant now = Instant.now();
        save(phone, 300, PaymentStatus.SUCCESS, null);
        save(phone, 200, PaymentStatus.PUSHED, null);
        save(phone, 999, PaymentStatus.FAILED, null);
        save(phone, 888, PaymentStatus.SUCCESS, now.minus(Duration.ofDays(2)));
        save("254700000199", 777, PaymentStatus.SUCCESS, null);

        Long total = repository.sumAmountSince(phone, COUNTED, now.minus(Duration.ofHours(24)));

        assertThat(total).isEqualTo(500L);
    }

    @Test
    void dailySumIsZeroWhenThereAreNoPayments() {
        Long total = repository.sumAmountSince("254700000104", COUNTED, Instant.now().minus(Duration.ofHours(24)));

        assertThat(total).isZero();
    }

    @Test
    void latestPaymentForPhoneIsTheNewest() {
        String phone = "254700000105";
        Instant now = Instant.now();
        save(phone, 100, PaymentStatus.SUCCESS, now.minus(Duration.ofHours(3)));
        Payment newest = save(phone, 200, PaymentStatus.PUSHED, now.minus(Duration.ofMinutes(1)));

        assertThat(repository.findFirstByPhoneOrderByCreatedAtDesc(phone).orElseThrow().getId())
                .isEqualTo(newest.getId());
    }

    @Test
    void rowLockedLookupsFindThePayment() {
        Payment p = new Payment(UUID.randomUUID().toString(), "254700000106", 100, "ORD-1");
        p.setStatus(PaymentStatus.PUSHED);
        p.setCheckoutRequestId("ws_CO_repo_lock_1");
        p = repository.saveAndFlush(p);

        assertThat(repository.findByIdForUpdate(p.getId())).isPresent();
        assertThat(repository.findByCheckoutRequestIdForUpdate("ws_CO_repo_lock_1")).isPresent();
        assertThat(repository.findByCheckoutRequestIdForUpdate("does-not-exist")).isEmpty();
    }

    @Test
    void findsPushedPaymentsThatHaveBeenWaitingLongEnough() {
        Payment p = save("254700000107", 100, PaymentStatus.PUSHED, null);

        assertThat(repository.findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                PaymentStatus.PUSHED, Instant.now().plus(Duration.ofMinutes(5))))
                .extracting(Payment::getId).contains(p.getId());
        assertThat(repository.findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                PaymentStatus.PUSHED, Instant.now().minus(Duration.ofHours(1))))
                .extracting(Payment::getId).doesNotContain(p.getId());
    }
}
