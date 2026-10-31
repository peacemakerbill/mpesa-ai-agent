package com.mganji.ganji.repository;

import com.mganji.ganji.model.ActionStatus;
import com.mganji.ganji.model.WalletAction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What the services need from the store for wallet actions. The same contract is implemented once for SQL
 * (PostgreSQL and MySQL, in the jpa package) and once for MongoDB (in the mongo package). The database is chosen
 * with the database type in application.yml.
 */
@NoRepositoryBean
public interface WalletActionRepository extends CrudRepository<WalletAction, UUID> {

    Optional<WalletAction> findByIdempotencyKey(String idempotencyKey);

    List<WalletAction> findByPhoneOrderByCreatedAtDesc(String phone, Pageable pageable);

    long countByPhoneAndStatusIn(String phone, Collection<ActionStatus> statuses);

    /** Row lock in SQL, so two taps (or the app and the chat) cannot move the same action at once. Version check in MongoDB. */
    Optional<WalletAction> findByIdForUpdate(UUID id);

    boolean existsBySmsCode(String smsCode);
}
