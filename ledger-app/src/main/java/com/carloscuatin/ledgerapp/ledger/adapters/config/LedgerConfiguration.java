package com.carloscuatin.ledgerapp.ledger.adapters.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.carloscuatin.ledgerapp.ledger.application.CreateTransfer;

/**
 * Wires the ledger module into the container. Use cases carry no Spring annotations, so they are built here, by hand,
 * from the settings they need.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LedgerProperties.class)
class LedgerConfiguration {

    @Bean
    CreateTransfer createTransfer(LedgerProperties properties) {
        return new CreateTransfer(properties.currencies());
    }
}
