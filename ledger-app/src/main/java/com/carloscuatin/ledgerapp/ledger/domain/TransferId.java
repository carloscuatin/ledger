package com.carloscuatin.ledgerapp.ledger.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifies a transfer across all of its states, with a type of its own so the compiler never mixes a transfer up
 * with any other identifier.
 */
public record TransferId(UUID value) {

    public TransferId {
        Objects.requireNonNull(value, "value");
    }
}
