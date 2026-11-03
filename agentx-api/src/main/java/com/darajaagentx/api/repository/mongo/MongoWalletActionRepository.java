package com.darajaagentx.api.repository.mongo;

import com.darajaagentx.api.model.WalletAction;
import com.darajaagentx.api.repository.WalletActionRepository;
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
