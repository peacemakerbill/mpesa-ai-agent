package com.darajaagentx.api.repository;

import com.darajaagentx.api.model.ApprovalCode;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@NoRepositoryBean
public interface ApprovalCodeRepository extends CrudRepository<ApprovalCode, UUID> {

    /** Row lock in SQL. In MongoDB the version number stops two guesses from both counting as the first. */
    Optional<ApprovalCode> findFirstByPhoneAndKindAndTargetIdAndUsedAtIsNullOrderByCreatedAtDesc(String phone, String kind, UUID targetId);

    /** The newest code for this request, used or not, with no lock. It only tells when the last one was sent. */
    Optional<ApprovalCode> findFirstByPhoneAndKindAndTargetIdOrderByCreatedAtDesc(String phone, String kind, UUID targetId);

    List<ApprovalCode> findByPhoneAndKindAndTargetIdAndUsedAtIsNull(String phone, String kind, UUID targetId);

    long countByPhoneAndCreatedAtAfter(String phone, Instant after);
}
