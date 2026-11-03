package com.carloscuatin.ledgerapp.ledger.application;

import java.util.Objects;

import com.carloscuatin.ledgerapp.ledger.domain.TenantId;

/**
 * The tenant of the running unit of work. There is deliberately no way to set it: it exists only during a call to
 * {@link #callAs} and ends with that call, even on failure, so nothing has to clean it up and it cannot leak to the
 * next use of a thread. A new thread does not inherit it, so work handed to another thread fails instead of running as
 * some other tenant.
 */
public final class CurrentTenant {

    private static final ScopedValue<TenantId> TENANT = ScopedValue.newInstance();

    private CurrentTenant() {
    }

    /** Runs the work in the scope of a tenant and returns its result; whatever the work throws passes through as is. */
    public static <R, X extends Throwable> R callAs(TenantId tenant, ScopedValue.CallableOp<R, X> work) throws X {
        Objects.requireNonNull(tenant, "tenant");
        // ScopedValue allows rebinding. A transaction that joins an open one does not begin again, so an inner scope
        // would see one tenant while the database connection still carries the other.
        if (TENANT.isBound()) {
            throw new IllegalStateException("A tenant is already in scope");
        }
        return ScopedValue.where(TENANT, tenant).call(work);
    }

    public static TenantId require() {
        return TENANT.orElseThrow(() -> new IllegalStateException("No tenant in scope"));
    }
}
