package com.darajaagentx.api.repository.jpa;

import com.darajaagentx.api.model.LinkCode;
import com.darajaagentx.api.repository.LinkCodeRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

/** PostgreSQL and MySQL. */
public interface JpaLinkCodeRepository extends JpaRepository<LinkCode, UUID>, LinkCodeRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LinkCode> findFirstByPhoneAndUsedAtIsNullOrderByCreatedAtDesc(String phone);
}
