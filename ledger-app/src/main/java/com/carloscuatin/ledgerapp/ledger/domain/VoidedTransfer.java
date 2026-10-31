package com.carloscuatin.ledgerapp.ledger.domain;

import java.util.Objects;

/**
 * A hold released on purpose before it was posted; no money moved.
 */
public record VoidedTransfer(TransferTerms terms) implements Transfer {

    public VoidedTransfer {
        Objects.requireNonNull(terms, "terms");
    }
}
