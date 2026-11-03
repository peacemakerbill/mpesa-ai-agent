package com.darajaagentx.api.repository;

import com.darajaagentx.api.model.EmailRecipient;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@NoRepositoryBean
public interface EmailRecipientRepository extends CrudRepository<EmailRecipient, UUID> {

    List<EmailRecipient> findByOwnerPhoneOrderByCreatedAtAsc(String ownerPhone);

    Optional<EmailRecipient> findByIdAndOwnerPhone(UUID id, String ownerPhone);

    Optional<EmailRecipient> findByOwnerPhoneAndEmail(String ownerPhone, String email);

    long countByOwnerPhone(String ownerPhone);
}
