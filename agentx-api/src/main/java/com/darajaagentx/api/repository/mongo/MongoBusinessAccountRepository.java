package com.darajaagentx.api.repository.mongo;

import com.darajaagentx.api.model.BusinessAccount;
import com.darajaagentx.api.repository.BusinessAccountRepository;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

/** MongoDB. */
public interface MongoBusinessAccountRepository extends MongoRepository<BusinessAccount, UUID>, BusinessAccountRepository {
}
