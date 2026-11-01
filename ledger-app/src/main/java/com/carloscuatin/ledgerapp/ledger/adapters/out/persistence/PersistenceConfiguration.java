package com.carloscuatin.ledgerapp.ledger.adapters.out.persistence;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.carloscuatin.ledgerapp.ledger.application.IdGenerator;

/**
 * Offers the outbound adapters of the ledger module to the container, as the ports the use cases declare. The adapter
 * classes stay package-private: nothing outside this package depends on them.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!migrate")
class PersistenceConfiguration {

    @Bean
    IdGenerator idGenerator() {
        return new UuidV7IdGenerator();
    }
}
