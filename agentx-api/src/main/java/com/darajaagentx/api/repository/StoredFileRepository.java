package com.darajaagentx.api.repository;

import com.darajaagentx.api.model.StoredFile;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.time.Instant;

@NoRepositoryBean
public interface StoredFileRepository extends CrudRepository<StoredFile, String> {

    /** Removes every file whose keep-until time has passed. Returns how many were removed. */
    long deleteByExpiresAtBefore(Instant before);
}
