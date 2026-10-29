package com.mpesaagent.companion.repository.mongo;

import com.mpesaagent.companion.model.WalletAction;
import com.mpesaagent.companion.repository.WalletActionRepository;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;
import java.util.UUID;

/** MongoDB. */
public interface MongoWalletActionRepository extends MongoRepository<WalletAction, UUID>, WalletActionRepository {

    /** No lock here. Saving checks the version number, so a second writer on the same action gets a conflict. */
    @Override
    @Query("{ '_id': ?0 }")
    Optional<WalletAction> findByIdForUpdate(UUID id);
}
