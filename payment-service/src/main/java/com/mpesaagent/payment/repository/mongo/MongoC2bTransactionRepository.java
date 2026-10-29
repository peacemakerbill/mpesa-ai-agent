package com.mpesaagent.payment.repository.mongo;

import com.mpesaagent.payment.model.C2bTransaction;
import com.mpesaagent.payment.repository.C2bTransactionRepository;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

/** MongoDB. */
public interface MongoC2bTransactionRepository extends MongoRepository<C2bTransaction, UUID>, C2bTransactionRepository {

    @Override
    default <S extends C2bTransaction> S saveAndFlush(S entity) {
        return save(entity);
    }
}
