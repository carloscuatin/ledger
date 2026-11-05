package com.carloscuatin.ledgerapp.ledger.adapters.out.persistence;

import org.springframework.boot.LazyInitializationExcludeFilter;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Flyway migrates when its initializer is created, and this process has nothing else that would ask for it: with lazy
 * initialization turned on, the process would start, do nothing and exit with success. A property in this profile could
 * not prevent that, because the environment overrides it. Excluding the type, and not declaring a bean that requires it,
 * leaves the process able to run with Flyway switched off.
 */
@Configuration(proxyBeanMethods = false)
@Profile("migrate")
class MigrateProfileConfiguration {

    /** Static because the filter is looked up while bean definitions are processed, before this class could be built. */
    @Bean
    static LazyInitializationExcludeFilter migrationRunsWithLazyInitialization() {
        return LazyInitializationExcludeFilter.forBeanTypes(FlywayMigrationInitializer.class);
    }
}
