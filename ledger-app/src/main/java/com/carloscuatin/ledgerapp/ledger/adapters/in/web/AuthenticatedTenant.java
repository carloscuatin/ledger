package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import org.springframework.security.core.context.SecurityContextHolder;

import com.carloscuatin.ledgerapp.ledger.application.CurrentTenant;
import com.carloscuatin.ledgerapp.ledger.domain.TenantId;

/**
 * Runs the work of a request as the tenant of its authenticated principal, which is the only place that tenant comes
 * from: row level security filters by whatever tenant it is told, so a tenant taken from anything the caller sends would
 * open another tenant's rows without any error. The scope covers the call to the use case and nothing else, so reading
 * the request and writing the response run without a tenant.
 */
final class AuthenticatedTenant {

    private AuthenticatedTenant() {
    }

    static <R, X extends Throwable> R call(ScopedValue.CallableOp<R, X> work) throws X {
        return CurrentTenant.callAs(tenant(), work);
    }

    /** Anything that is not a principal with a tenant is refused, so an unknown kind of principal fails closed. */
    private static TenantId tenant() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof TenantPrincipal principal) {
            return principal.tenant().orElseThrow(TenantRequiredException::new);
        }
        throw new TenantRequiredException();
    }
}
