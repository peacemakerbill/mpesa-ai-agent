package com.mpesaagent.companion.repository;

import com.mpesaagent.companion.model.LinkCode;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@NoRepositoryBean
public interface LinkCodeRepository extends CrudRepository<LinkCode, UUID> {

    /** Row lock in SQL. In MongoDB the version number stops two guesses from both counting as the first. */
    Optional<LinkCode> findFirstByPhoneAndUsedAtIsNullOrderByCreatedAtDesc(String phone);

    List<LinkCode> findByPhoneAndUsedAtIsNull(String phone);

    long countByPhoneAndCreatedAtAfter(String phone, Instant after);
}
