package com.darajaagentx.api.repository.jpa;

import com.darajaagentx.api.model.StoredFile;
import com.darajaagentx.api.repository.StoredFileRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** PostgreSQL and MySQL. */
public interface JpaStoredFileRepository extends JpaRepository<StoredFile, String>, StoredFileRepository {

    @Override
    @Transactional
    long deleteByExpiresAtBefore(Instant before);
}
