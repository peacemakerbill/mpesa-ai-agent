package com.darajaagentx.api.repository.jpa;

import com.darajaagentx.api.model.ApprovalCode;
import com.darajaagentx.api.repository.ApprovalCodeRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

/** PostgreSQL and MySQL. */
public interface JpaApprovalCodeRepository extends JpaRepository<ApprovalCode, UUID>, ApprovalCodeRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ApprovalCode> findFirstByPhoneAndKindAndTargetIdAndUsedAtIsNullOrderByCreatedAtDesc(String phone, String kind, UUID targetId);
}
