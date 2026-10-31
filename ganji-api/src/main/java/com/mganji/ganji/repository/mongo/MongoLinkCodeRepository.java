package com.mganji.ganji.repository.mongo;

import com.mganji.ganji.model.LinkCode;
import com.mganji.ganji.repository.LinkCodeRepository;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

/** MongoDB. */
public interface MongoLinkCodeRepository extends MongoRepository<LinkCode, UUID>, LinkCodeRepository {
}
