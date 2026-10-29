package com.mpesaagent.payment.repository.mongo;

import com.mpesaagent.payment.model.Payment;
import com.mpesaagent.payment.model.PaymentStatus;
import com.mpesaagent.payment.repository.PaymentRepository;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** MongoDB. No row locks here. Saving checks the version number, so a second writer on the same payment gets a conflict. */
public interface MongoPaymentRepository extends MongoRepository<Payment, UUID>, PaymentRepository {

    @Override
    default <S extends Payment> S saveAndFlush(S entity) {
        return save(entity);
    }

    @Override
    @Query("{ '_id': ?0 }")
    Optional<Payment> findByIdForUpdate(UUID id);

    @Override
    @Query("{ 'checkoutRequestId': ?0 }")
    Optional<Payment> findByCheckoutRequestIdForUpdate(String checkoutRequestId);

    List<Payment> findByPhoneAndStatusInAndCreatedAtAfter(String phone, Collection<PaymentStatus> statuses, Instant since);

    /** Adds up in Java. A customer has a handful of payments a day, so this stays small. */
    @Override
    default Long sumAmountSince(String phone, Collection<PaymentStatus> statuses, Instant since) {
        return findByPhoneAndStatusInAndCreatedAtAfter(phone, statuses, since).stream().mapToLong(Payment::getAmount).sum();
    }
}
