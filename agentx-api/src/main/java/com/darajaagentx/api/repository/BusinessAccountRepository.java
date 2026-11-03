package com.darajaagentx.api.repository;

import com.darajaagentx.api.model.BusinessAccount;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@NoRepositoryBean
public interface BusinessAccountRepository extends CrudRepository<BusinessAccount, UUID> {

    List<BusinessAccount> findByOwnerPhoneOrderByCreatedAtAsc(String ownerPhone);

    Optional<BusinessAccount> findByIdAndOwnerPhone(UUID id, String ownerPhone);

    List<BusinessAccount> findByOwnerPhoneAndParentId(String ownerPhone, UUID parentId);
}
