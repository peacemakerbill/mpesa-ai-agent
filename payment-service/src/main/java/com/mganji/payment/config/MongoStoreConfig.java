package com.mganji.payment.config;

import com.mganji.payment.model.Payment;
import com.mganji.payment.model.MpesaOperation;
import com.mganji.payment.model.C2bTransaction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;

/**
 * Everything that is different when the database type is mongodb: the MongoDB repositories, ids and timestamps
 * (MongoDB does not make them), and a transaction manager that does not need a database.
 */
@Configuration(proxyBeanMethods = false)
@Profile("mongodb")
@EnableMongoRepositories(basePackages = "com.mganji.payment.repository.mongo")
public class MongoStoreConfig {

    /**
     * Keeps @Transactional and "after commit" events working without a SQL database. It does not roll data back.
     * Each save changes one document, and concurrent writers are stopped by the version number on that document.
     */
    @Bean
    PlatformTransactionManager transactionManager() {
        return new NoOpTransactionManager();
    }

    @Bean
    BeforeConvertCallback<Payment> paymentBeforeConvert() {
        return (entity, collection) -> {
            entity.prepareForMongo(Instant.now());
            return entity;
        };
    }

    @Bean
    BeforeConvertCallback<MpesaOperation> mpesaOperationBeforeConvert() {
        return (entity, collection) -> {
            entity.prepareForMongo(Instant.now());
            return entity;
        };
    }

    @Bean
    BeforeConvertCallback<C2bTransaction> c2bTransactionBeforeConvert() {
        return (entity, collection) -> {
            entity.prepareForMongo(Instant.now());
            return entity;
        };
    }
}
