package com.darajaagentx.api.repository.mongo;

import com.darajaagentx.api.model.ApprovalCode;
import com.darajaagentx.api.repository.ApprovalCodeRepository;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

/** MongoDB. */
public interface MongoApprovalCodeRepository extends MongoRepository<ApprovalCode, UUID>, ApprovalCodeRepository {
}
