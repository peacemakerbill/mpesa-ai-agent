package com.darajaagentx.api.repository.mongo;

import com.darajaagentx.api.model.EmailLog;
import com.darajaagentx.api.repository.EmailLogRepository;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

/** MongoDB. */
public interface MongoEmailLogRepository extends MongoRepository<EmailLog, UUID>, EmailLogRepository {
}
