package com.darajaagentx.api.repository.mongo;

import com.darajaagentx.api.model.EmailRecipient;
import com.darajaagentx.api.repository.EmailRecipientRepository;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

/** MongoDB. */
public interface MongoEmailRecipientRepository extends MongoRepository<EmailRecipient, UUID>, EmailRecipientRepository {
}
