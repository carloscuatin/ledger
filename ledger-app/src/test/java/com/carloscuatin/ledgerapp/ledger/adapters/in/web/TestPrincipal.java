package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import java.util.Optional;

import com.carloscuatin.ledgerapp.ledger.domain.TenantId;

/** Stands in for what a mechanism that verifies credentials would produce. It exists only on the test classpath. */
record TestPrincipal(Optional<TenantId> tenant) implements TenantPrincipal {

    @Override
    public String getName() {
        return "test-principal";
    }
}
