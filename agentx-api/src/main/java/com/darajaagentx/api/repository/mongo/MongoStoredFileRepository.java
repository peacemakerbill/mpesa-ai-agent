package com.darajaagentx.api.repository.mongo;

import com.darajaagentx.api.model.StoredFile;
import com.darajaagentx.api.repository.StoredFileRepository;
import org.springframework.data.mongodb.repository.MongoRepository;

/** MongoDB. A document holds at most 16 MB, which is why the database store has its own size limit. */
public interface MongoStoredFileRepository extends MongoRepository<StoredFile, String>, StoredFileRepository {
}
