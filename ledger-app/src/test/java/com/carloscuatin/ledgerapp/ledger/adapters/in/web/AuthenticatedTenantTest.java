package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import com.carloscuatin.ledgerapp.ledger.application.CurrentTenant;
import com.carloscuatin.ledgerapp.ledger.domain.TenantId;

class AuthenticatedTenantTest {

    private static final TenantId TENANT = new TenantId(UUID.fromString("00000000-0000-7000-8000-00000000000a"));

    @AfterEach
    void clearTheContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theWorkRunsAsTheTenantOfThePrincipal() {
        authenticate(principalOf(new TestPrincipal(Optional.of(TENANT))));

        assertThat(AuthenticatedTenant.call(CurrentTenant::require)).isEqualTo(TENANT);
    }

    @Test
    void theScopeEndsWithTheCall() {
        authenticate(principalOf(new TestPrincipal(Optional.of(TENANT))));

        AuthenticatedTenant.call(CurrentTenant::require);

        assertThatThrownBy(CurrentTenant::require).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void whatTheWorkThrowsPassesThrough() {
        authenticate(principalOf(new TestPrincipal(Optional.of(TENANT))));
        var failure = new IllegalArgumentException("from the work");

        assertThatThrownBy(() -> AuthenticatedTenant.call(() -> {
            throw failure;
        })).isSameAs(failure);
    }

    @Test
    void aPrincipalWithoutATenantNeverRunsTheWork() {
        authenticate(principalOf(new TestPrincipal(Optional.empty())));

        assertTheWorkNeverRuns();
    }

    @Test
    void aPrincipalOfAnyOtherKindNeverRunsTheWork() {
        authenticate(principalOf("someone"));

        assertTheWorkNeverRuns();
    }

    @Test
    void anAnonymousRequestNeverRunsTheWork() {
        authenticate(new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertTheWorkNeverRuns();
    }

    @Test
    void noAuthenticationNeverRunsTheWork() {
        assertTheWorkNeverRuns();
    }

    private static void assertTheWorkNeverRuns() {
        var ran = new AtomicBoolean();

        assertThatThrownBy(() -> AuthenticatedTenant.call(() -> ran.getAndSet(true)))
                .isInstanceOf(TenantRequiredException.class);

        assertThat(ran).isFalse();
    }

    private static Authentication principalOf(Object principal) {
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
    }

    private static void authenticate(Authentication authentication) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
