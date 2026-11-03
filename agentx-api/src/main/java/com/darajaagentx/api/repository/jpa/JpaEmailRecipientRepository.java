package com.darajaagentx.api.repository.jpa;

import com.darajaagentx.api.model.EmailRecipient;
import com.darajaagentx.api.repository.EmailRecipientRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** PostgreSQL and MySQL. */
public interface JpaEmailRecipientRepository extends JpaRepository<EmailRecipient, UUID>, EmailRecipientRepository {
}
