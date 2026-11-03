package com.darajaagentx.api.repository.jpa;

import com.darajaagentx.api.model.BusinessAccount;
import com.darajaagentx.api.repository.BusinessAccountRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** PostgreSQL and MySQL. */
public interface JpaBusinessAccountRepository extends JpaRepository<BusinessAccount, UUID>, BusinessAccountRepository {
}
