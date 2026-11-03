package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.config.Customizer;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.Filter;

/**
 * Tests the instrument the authenticated tests rely on, and doubles as the control for what
 * {@link UnauthenticatedAccessIT} says about production: the probes used there do see a customizer when there is one.
 */
class TestAuthenticationIT extends AuthenticatedApi {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private SecurityFilterChain chain;

    @Test
    void theHeaderIsWhatGetsARequestPastTheChain() throws Exception {
        var path = "/v1/nope";

        assertThat(send(request(path)).statusCode()).isEqualTo(401);
        assertThat(send(requestAs(UUID.randomUUID(), path)).statusCode()).isEqualTo(404);
    }

    @Test
    void theTestPrincipalAddsOneFilterToTheProductionChainAndRemovesNone() {
        var expected = new ArrayList<>(UnauthenticatedAccessIT.PRODUCTION_FILTERS);
        expected.add(expected.indexOf("AnonymousAuthenticationFilter"), "TestPrincipalFilter");

        assertThat(chain.getFilters().stream().map(Filter::getClass).map(Class::getSimpleName))
                .containsExactlyElementsOf(expected);
        assertThat(context.getBeanNamesForType(Customizer.class)).hasSize(1);
    }
}
