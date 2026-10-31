package com.mganji.payment.repository.jpa;

import com.mganji.payment.model.Payment;
import com.mganji.payment.model.PaymentStatus;
import com.mganji.payment.repository.PaymentRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL and MySQL. */
public interface JpaPaymentRepository extends JpaRepository<Payment, UUID>, PaymentRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.checkoutRequestId = :checkoutRequestId")
    Optional<Payment> findByCheckoutRequestIdForUpdate(@Param("checkoutRequestId") String checkoutRequestId);

    @Override
    @Query("""
            select coalesce(sum(p.amount), 0)
            from Payment p
            where p.phone = :phone
              and p.status in :statuses
              and p.createdAt > :since
            """)
    Long sumAmountSince(@Param("phone") String phone,
                        @Param("statuses") Collection<PaymentStatus> statuses,
                        @Param("since") Instant since);
}
