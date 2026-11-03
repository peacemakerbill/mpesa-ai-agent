package com.darajaagentx.api.repository.mongo;

import com.darajaagentx.api.model.LinkCode;
import com.darajaagentx.api.repository.LinkCodeRepository;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

/** MongoDB. */
public interface MongoLinkCodeRepository extends MongoRepository<LinkCode, UUID>, LinkCodeRepository {
}
