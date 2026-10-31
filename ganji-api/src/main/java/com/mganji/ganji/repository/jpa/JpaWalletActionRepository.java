package com.mganji.ganji.repository.jpa;

import com.mganji.ganji.model.WalletAction;
import com.mganji.ganji.repository.WalletActionRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/** PostgreSQL and MySQL. */
public interface JpaWalletActionRepository extends JpaRepository<WalletAction, UUID>, WalletActionRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from WalletAction a where a.id = :id")
    Optional<WalletAction> findByIdForUpdate(@Param("id") UUID id);
}
