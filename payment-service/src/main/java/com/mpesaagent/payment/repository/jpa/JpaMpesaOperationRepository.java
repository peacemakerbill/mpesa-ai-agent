package com.mpesaagent.payment.repository.jpa;

import com.mpesaagent.payment.model.MpesaOperation;
import com.mpesaagent.payment.model.OperationStatus;
import com.mpesaagent.payment.model.OperationType;
import com.mpesaagent.payment.repository.MpesaOperationRepository;
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
public interface JpaMpesaOperationRepository extends JpaRepository<MpesaOperation, UUID>, MpesaOperationRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from MpesaOperation o where o.id = :id")
    Optional<MpesaOperation> findByIdForUpdate(@Param("id") UUID id);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from MpesaOperation o where o.originatorConversationId = :conversationId")
    Optional<MpesaOperation> findByOriginatorConversationIdForUpdate(@Param("conversationId") String conversationId);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from MpesaOperation o where o.conversationId = :conversationId")
    Optional<MpesaOperation> findByConversationIdForUpdate(@Param("conversationId") String conversationId);

    @Override
    @Query("""
            select coalesce(sum(o.amount), 0)
            from MpesaOperation o
            where o.type in :types
              and o.status in :statuses
              and o.createdAt > :since
            """)
    Long sumAmountSince(@Param("types") Collection<OperationType> types,
                        @Param("statuses") Collection<OperationStatus> statuses,
                        @Param("since") Instant since);

    @Override
    @Query("""
            select coalesce(sum(o.amount), 0)
            from MpesaOperation o
            where o.relatedPaymentId = :paymentId
              and o.type = :type
              and o.status in :statuses
            """)
    Long sumForPayment(@Param("paymentId") UUID paymentId,
                       @Param("type") OperationType type,
                       @Param("statuses") Collection<OperationStatus> statuses);
}
