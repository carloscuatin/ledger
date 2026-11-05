package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.DispatcherType;
import tools.jackson.databind.json.JsonMapper;

/**
 * The filters every request crosses before reaching a controller. The chain demands an authenticated principal and
 * offers no way to become one, so everything is refused until a mechanism that verifies credentials is added to it.
 */
@Configuration(proxyBeanMethods = false)
@Profile("api")
class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper json) throws Exception {
        return http
                // A failed request is forwarded to the error page, and that forward crosses the chain again: refusing
                // it would turn the real failure into a 401. A client asking for the page directly is an ordinary
                // request, so it stays refused.
                .authorizeHttpRequests(requests -> requests.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .anyRequest().authenticated())
                // Without cookies there is no session for a forged request to ride on, and none to keep.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Its filter runs ahead of the rules above and would redirect anyone who asks for its route.
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(
                        failures -> failures.authenticationEntryPoint(new ProblemAuthenticationEntryPoint(json)))
                .build();
    }
}
