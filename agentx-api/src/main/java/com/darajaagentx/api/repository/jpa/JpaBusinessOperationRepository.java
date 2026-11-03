package com.darajaagentx.api.repository.jpa;

import com.darajaagentx.api.model.BusinessOperation;
import com.darajaagentx.api.repository.BusinessOperationRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** PostgreSQL and MySQL. */
public interface JpaBusinessOperationRepository extends JpaRepository<BusinessOperation, UUID>, BusinessOperationRepository {
}
