package com.mpesaagent.payment.repository;

import com.mpesaagent.payment.model.Payment;
import com.mpesaagent.payment.model.PaymentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.NoRepositoryBean;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What the services need from the store for payments. Implemented once for SQL (PostgreSQL and MySQL, jpa package)
 * and once for MongoDB (mongo package). The database is chosen with the database type in application.yml.
 */
@NoRepositoryBean
public interface PaymentRepository extends FlushingRepository<Payment, UUID> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByCheckoutRequestId(String checkoutRequestId);

    Optional<Payment> findFirstByPhoneOrderByCreatedAtDesc(String phone);

    List<Payment> findByPhoneOrderByCreatedAtDesc(String phone, Pageable pageable);

    /** Row lock in SQL, so a callback and the reconciler cannot update the same payment at once. Version check in MongoDB. */
    Optional<Payment> findByIdForUpdate(UUID id);

    Optional<Payment> findByCheckoutRequestIdForUpdate(String checkoutRequestId);

    Long sumAmountSince(String phone, Collection<PaymentStatus> statuses, Instant since);

    List<Payment> findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(PaymentStatus status, Instant cutoff);

    List<Payment> findTop50ByStatusAndCreatedAtBetweenOrderByCreatedAtAsc(PaymentStatus status, Instant from, Instant to);
}
