package com.carloscuatin.ledgerapp.ledger.adapters.out.persistence;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import com.carloscuatin.ledgerapp.ledger.application.IdGenerator;

/**
 * Offers the outbound adapters of the ledger module to the container, as the ports the use cases declare. The adapter
 * classes stay package-private: nothing outside this package depends on them.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!migrate")
class PersistenceConfiguration {

    /** Replaces the adapter Spring Boot would create, as the way to give the transaction manager this dialect. */
    @Bean
    JpaVendorAdapter jpaVendorAdapter() {
        var dialect = new TenantHibernateJpaDialect();
        return new HibernateJpaVendorAdapter() {
            @Override
            public HibernateJpaDialect getJpaDialect() {
                return dialect;
            }
        };
    }

    @Bean
    IdGenerator idGenerator() {
        return new UuidV7IdGenerator();
    }
}
