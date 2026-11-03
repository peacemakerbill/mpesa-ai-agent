package com.darajaagentx.payment.repository;

import com.darajaagentx.payment.model.C2bTransaction;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.UUID;

@NoRepositoryBean
public interface C2bTransactionRepository extends FlushingRepository<C2bTransaction, UUID> {

    boolean existsByTransId(String transId);
}
