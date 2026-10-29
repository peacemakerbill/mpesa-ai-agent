package com.mpesaagent.companion.repository.mongo;

import com.mpesaagent.companion.model.LinkCode;
import com.mpesaagent.companion.repository.LinkCodeRepository;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

/** MongoDB. */
public interface MongoLinkCodeRepository extends MongoRepository<LinkCode, UUID>, LinkCodeRepository {
}
