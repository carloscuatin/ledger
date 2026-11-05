package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import com.carloscuatin.ledgerapp.ledger.domain.TenantId;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Gives a test a principal through a header, on the production security chain: this adds one filter to that chain and
 * replaces nothing, so what a test sees is what production does once a request is authenticated.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestAuthentication {

    static final String PRINCIPAL_HEADER = "X-Test-Principal";
    static final String NO_TENANT = "no-tenant";

    /** A filter handed to the chain and not declared as a bean, which Spring Boot would also register on the server. */
    @Bean
    Customizer<HttpSecurity> principalFromATestHeader() {
        return http -> http.addFilterBefore(new TestPrincipalFilter(), AnonymousAuthenticationFilter.class);
    }

    static final class TestPrincipalFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            var header = request.getHeader(PRINCIPAL_HEADER);
            if (header != null) {
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(principal(header), null, List.of()));
                SecurityContextHolder.setContext(context);
            }
            chain.doFilter(request, response);
        }

        private static TestPrincipal principal(String header) {
            return NO_TENANT.equals(header)
                    ? new TestPrincipal(Optional.empty())
                    : new TestPrincipal(Optional.of(new TenantId(UUID.fromString(header))));
        }
    }
}
