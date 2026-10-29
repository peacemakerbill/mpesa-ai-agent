package com.mpesaagent.companion.config;

import com.mpesaagent.companion.model.WalletAction;
import com.mpesaagent.companion.model.Device;
import com.mpesaagent.companion.model.LinkCode;
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
@EnableMongoRepositories(basePackages = "com.mpesaagent.companion.repository.mongo")
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
    BeforeConvertCallback<WalletAction> walletActionBeforeConvert() {
        return (entity, collection) -> {
            entity.prepareForMongo(Instant.now());
            return entity;
        };
    }

    @Bean
    BeforeConvertCallback<Device> deviceBeforeConvert() {
        return (entity, collection) -> {
            entity.prepareForMongo(Instant.now());
            return entity;
        };
    }

    @Bean
    BeforeConvertCallback<LinkCode> linkCodeBeforeConvert() {
        return (entity, collection) -> {
            entity.prepareForMongo(Instant.now());
            return entity;
        };
    }
}
