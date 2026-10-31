package com.mganji.payment.repository;

import com.mganji.payment.model.C2bTransaction;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.UUID;

@NoRepositoryBean
public interface C2bTransactionRepository extends FlushingRepository<C2bTransaction, UUID> {

    boolean existsByTransId(String transId);
}
