package com.carloscuatin.ledgerapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * Without the exclusion, Spring Boot creates a user with a generated password and prints it, which is a way in that
 * nobody chose. It lives here and not in a property because a profile that sets {@code spring.autoconfigure.exclude}
 * replaces the list instead of adding to it.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class LedgerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerApplication.class, args);
    }
}
