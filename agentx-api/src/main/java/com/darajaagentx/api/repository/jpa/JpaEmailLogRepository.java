package com.darajaagentx.api.repository.jpa;

import com.darajaagentx.api.model.EmailLog;
import com.darajaagentx.api.repository.EmailLogRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** PostgreSQL and MySQL. */
public interface JpaEmailLogRepository extends JpaRepository<EmailLog, UUID>, EmailLogRepository {
}
