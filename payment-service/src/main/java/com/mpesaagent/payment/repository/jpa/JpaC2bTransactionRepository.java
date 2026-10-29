package com.mpesaagent.payment.repository.jpa;

import com.mpesaagent.payment.model.C2bTransaction;
import com.mpesaagent.payment.repository.C2bTransactionRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** PostgreSQL and MySQL. */
public interface JpaC2bTransactionRepository extends JpaRepository<C2bTransaction, UUID>, C2bTransactionRepository {
}
