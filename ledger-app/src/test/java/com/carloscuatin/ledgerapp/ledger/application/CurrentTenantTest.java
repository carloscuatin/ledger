package com.carloscuatin.ledgerapp.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.carloscuatin.ledgerapp.ledger.domain.TenantId;

class CurrentTenantTest {

    private static final TenantId TENANT_A = new TenantId(UUID.fromString("00000000-0000-7000-8000-00000000000a"));
    private static final TenantId TENANT_B = new TenantId(UUID.fromString("00000000-0000-7000-8000-00000000000b"));
    private static final String NO_TENANT = "No tenant in scope";

    /** Checked on purpose: the scope has to let any failure of the work through untouched. */
    private static final class WorkFailure extends Exception {
        private static final long serialVersionUID = 1L;
    }

    private static void assertNoTenantInScope() {
        assertThatThrownBy(CurrentTenant::require)
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage(NO_TENANT);
    }

    @Test
    void workInsideAScopeSeesItsTenant() {
        assertThat(CurrentTenant.callAs(TENANT_A, CurrentTenant::require)).isEqualTo(TENANT_A);
    }

    @Test
    void theScopeReturnsWhatTheWorkReturned() {
        assertThat(CurrentTenant.callAs(TENANT_A, () -> "result")).isEqualTo("result");
    }

    @Test
    void workOutsideAnyScopeHasNoTenant() {
        assertNoTenantInScope();
    }

    @Test
    void theScopeEndsWhenTheWorkSucceeds() {
        CurrentTenant.callAs(TENANT_A, CurrentTenant::require);

        assertNoTenantInScope();
    }

    @Test
    void theScopeEndsWhenTheWorkFailsAndTheFailureIsNotWrapped() {
        var failure = new WorkFailure();

        assertThatThrownBy(() -> CurrentTenant.callAs(TENANT_A, () -> {
            throw failure;
        })).isSameAs(failure);

        assertNoTenantInScope();
    }

    @Test
    void aScopeCannotBeNested() {
        var innerWorkRan = new AtomicBoolean();

        CurrentTenant.callAs(TENANT_A, () -> {
            assertThatThrownBy(() -> CurrentTenant.callAs(TENANT_B, () -> innerWorkRan.getAndSet(true)))
                    .isExactlyInstanceOf(IllegalStateException.class)
                    .hasMessage("A tenant is already in scope");
            return null;
        });

        assertThat(innerWorkRan).isFalse();
    }

    @Test
    void anotherThreadDoesNotInheritTheTenant() throws InterruptedException {
        var failure = new AtomicReference<Throwable>();

        var inner = CurrentTenant.callAs(TENANT_A, () -> Thread.ofVirtual().start(() -> {
            try {
                CurrentTenant.require();
            } catch (IllegalStateException e) {
                failure.set(e);
            }
        }));
        inner.join();

        assertThat(failure.get()).isExactlyInstanceOf(IllegalStateException.class).hasMessage(NO_TENANT);
    }

    @Test
    void aNullTenantIsRejectedAndTheWorkDoesNotRun() {
        var workRan = new AtomicBoolean();

        assertThatThrownBy(() -> CurrentTenant.callAs(null, () -> workRan.getAndSet(true)))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("tenant");

        assertThat(workRan).isFalse();
    }
}
