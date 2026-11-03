package com.darajaagentx.api.repository;

import com.darajaagentx.api.model.EmailLog;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.UUID;

@NoRepositoryBean
public interface EmailLogRepository extends CrudRepository<EmailLog, UUID> {

    List<EmailLog> findTop30ByOwnerPhoneOrderByCreatedAtDesc(String ownerPhone);
}
