package com.carloscuatin.ledgerapp.ledger.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifies the tenant a unit of work belongs to, with a type of its own so the compiler never mixes a tenant up with
 * any other identifier.
 */
public record TenantId(UUID value) {

    public TenantId {
        Objects.requireNonNull(value, "value");
    }
}
