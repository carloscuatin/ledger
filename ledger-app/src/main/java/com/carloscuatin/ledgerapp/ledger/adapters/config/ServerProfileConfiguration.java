package com.carloscuatin.ledgerapp.ledger.adapters.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Only the api profile declares the security filter chain, and without one Spring Boot adds its own, with a login form. So
 * a process that was made a server by hand, through the web application type, is refused. The condition sees the context
 * itself and not a property, because by the time it runs the application has already chosen that context, and however the
 * type was set, it is the context that opens a port. A post-processor, for the reasons given in
 * {@link ExclusiveProfilesConfiguration}: nothing is migrated and no port is opened before it runs.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!api")
@ConditionalOnWebApplication
class ServerProfileConfiguration {

    /** Static so that running it does not require building this class first. */
    @Bean
    static BeanFactoryPostProcessor serverWithoutTheApiProfileRefusal() {
        return beanFactory -> {
            throw new IllegalStateException("This process would open a port without the api profile, the only one"
                    + " that declares the security filter chain: Spring Boot's default chain would serve the port instead.");
        };
    }
}
