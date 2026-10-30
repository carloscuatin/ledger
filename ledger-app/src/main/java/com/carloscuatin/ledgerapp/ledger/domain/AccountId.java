package com.carloscuatin.ledgerapp.ledger.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * References an account by its identifier, with a type of its own so the compiler never mixes an account up with any
 * other identifier.
 */
public record AccountId(UUID value) {
    public AccountId {
        Objects.requireNonNull(value, "value");
    }
}
