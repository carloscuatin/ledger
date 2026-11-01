package com.carloscuatin.ledgerapp.ledger.adapters.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.carloscuatin.ledgerapp.ledger.application.CreateTransfer;
import com.carloscuatin.ledgerapp.ledger.application.IdGenerator;

/**
 * Wires the ledger module into the container. Use cases carry no Spring annotations, so they are built here, by hand,
 * from the settings and ports they need.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!migrate")
@EnableConfigurationProperties(LedgerProperties.class)
class LedgerConfiguration {

    @Bean
    CreateTransfer createTransfer(LedgerProperties properties, IdGenerator ids) {
        return new CreateTransfer(properties.currencies(), ids);
    }
}
