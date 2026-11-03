package com.darajaagentx.api.repository.mongo;

import com.darajaagentx.api.model.BusinessOperation;
import com.darajaagentx.api.repository.BusinessOperationRepository;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

/** MongoDB. */
public interface MongoBusinessOperationRepository extends MongoRepository<BusinessOperation, UUID>, BusinessOperationRepository {
}
