package com.darajaagentx.api.repository;

import com.darajaagentx.api.model.BusinessOperation;
import com.darajaagentx.api.model.BusinessOperationStatus;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@NoRepositoryBean
public interface BusinessOperationRepository extends CrudRepository<BusinessOperation, UUID> {

    Optional<BusinessOperation> findByIdAndOwnerPhone(UUID id, String ownerPhone);

    Optional<BusinessOperation> findByOwnerPhoneAndIdempotencyKey(String ownerPhone, String idempotencyKey);

    List<BusinessOperation> findTop30ByOwnerPhoneOrderByCreatedAtDesc(String ownerPhone);

    List<BusinessOperation> findByOwnerPhoneAndStatus(String ownerPhone, BusinessOperationStatus status);
}
