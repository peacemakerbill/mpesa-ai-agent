package com.mganji.payment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Turns on the SQL repositories (PostgreSQL and MySQL). Only the jpa package is scanned, so the MongoDB
 * repositories, which use the same model classes, are never picked up here.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!mongodb")
@EnableJpaRepositories(basePackages = "com.mganji.payment.repository.jpa")
public class JpaRepositoriesConfig {
}
