package com.carloscuatin.ledgerapp.ledger.adapters.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.carloscuatin.ledgerapp.ledger.application.CreateTransfer;
import com.carloscuatin.ledgerapp.ledger.application.FindTransfer;
import com.carloscuatin.ledgerapp.ledger.application.IdGenerator;
import com.carloscuatin.ledgerapp.ledger.application.TransferQueries;
import com.carloscuatin.ledgerapp.ledger.application.Transfers;

/**
 * Wires the ledger module into the container. The only Spring annotation a use case carries is its transaction
 * boundary, so they are built here, by hand, from the settings and ports they need.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!migrate")
@EnableConfigurationProperties(LedgerProperties.class)
class LedgerConfiguration {

    @Bean
    CreateTransfer createTransfer(LedgerProperties properties, IdGenerator ids, Transfers transfers) {
        return new CreateTransfer(properties.currencies(), ids, transfers);
    }

    @Bean
    FindTransfer findTransfer(TransferQueries queries) {
        return new FindTransfer(queries);
    }
}
