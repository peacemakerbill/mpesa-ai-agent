package com.mganji.payment.repository;

import com.mganji.payment.model.MpesaOperation;
import com.mganji.payment.model.OperationStatus;
import com.mganji.payment.model.OperationType;
import org.springframework.data.repository.NoRepositoryBean;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@NoRepositoryBean
public interface MpesaOperationRepository extends FlushingRepository<MpesaOperation, UUID> {

    Optional<MpesaOperation> findByIdempotencyKey(String idempotencyKey);

    Optional<MpesaOperation> findByIdForUpdate(UUID id);

    Optional<MpesaOperation> findByOriginatorConversationIdForUpdate(String conversationId);

    Optional<MpesaOperation> findByConversationIdForUpdate(String conversationId);

    List<MpesaOperation> findTop20ByStatusOrderByCreatedAtAsc(OperationStatus status);

    List<MpesaOperation> findTop20ByRequestedByOrderByCreatedAtDesc(String requestedBy);

    /** Money already sent or committed in a period, used for the daily payout limit. */
    Long sumAmountSince(Collection<OperationType> types, Collection<OperationStatus> statuses, Instant since);

    /** Total already refunded or being refunded for one payment. */
    Long sumForPayment(UUID paymentId, OperationType type, Collection<OperationStatus> statuses);
}
