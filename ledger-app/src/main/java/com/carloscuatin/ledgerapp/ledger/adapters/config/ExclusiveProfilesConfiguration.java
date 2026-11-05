package com.carloscuatin.ledgerapp.ledger.adapters.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Serving requests and changing the schema are separate processes with separate roles. The refusal is a post-processor
 * because the container runs those before it creates any ordinary bean: it holds whatever the order of the beans, and
 * with lazy initialization too, when a bean that nothing depends on would never be created at all.
 */
@Configuration(proxyBeanMethods = false)
@Profile("api & migrate")
class ExclusiveProfilesConfiguration {

    /** Static so that running it does not require building this class first. */
    @Bean
    static BeanFactoryPostProcessor apiAndMigrateRefusal() {
        return beanFactory -> {
            throw new IllegalStateException("The api and migrate profiles must not run in the same process: api serves"
                    + " requests with the application's role, and migrate changes the schema with its own.");
        };
    }
}
