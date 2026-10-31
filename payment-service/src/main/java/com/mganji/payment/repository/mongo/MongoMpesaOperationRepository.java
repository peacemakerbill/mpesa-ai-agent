package com.mganji.payment.repository.mongo;

import com.mganji.payment.model.MpesaOperation;
import com.mganji.payment.model.OperationStatus;
import com.mganji.payment.model.OperationType;
import com.mganji.payment.repository.MpesaOperationRepository;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** MongoDB. No row locks here. Saving checks the version number, so a second writer on the same operation gets a conflict. */
public interface MongoMpesaOperationRepository extends MongoRepository<MpesaOperation, UUID>, MpesaOperationRepository {

    @Override
    default <S extends MpesaOperation> S saveAndFlush(S entity) {
        return save(entity);
    }

    @Override
    @Query("{ '_id': ?0 }")
    Optional<MpesaOperation> findByIdForUpdate(UUID id);

    @Override
    @Query("{ 'originatorConversationId': ?0 }")
    Optional<MpesaOperation> findByOriginatorConversationIdForUpdate(String conversationId);

    @Override
    @Query("{ 'conversationId': ?0 }")
    Optional<MpesaOperation> findByConversationIdForUpdate(String conversationId);

    List<MpesaOperation> findByTypeInAndStatusInAndCreatedAtAfter(Collection<OperationType> types,
                                                                  Collection<OperationStatus> statuses, Instant since);

    List<MpesaOperation> findByRelatedPaymentIdAndTypeAndStatusIn(UUID paymentId, OperationType type,
                                                                  Collection<OperationStatus> statuses);

    /** Adds up in Java. The daily payout limit only looks at one day of operations. */
    @Override
    default Long sumAmountSince(Collection<OperationType> types, Collection<OperationStatus> statuses, Instant since) {
        return findByTypeInAndStatusInAndCreatedAtAfter(types, statuses, since).stream()
                .map(MpesaOperation::getAmount).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
    }

    @Override
    default Long sumForPayment(UUID paymentId, OperationType type, Collection<OperationStatus> statuses) {
        return findByRelatedPaymentIdAndTypeAndStatusIn(paymentId, type, statuses).stream()
                .map(MpesaOperation::getAmount).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
    }
}
