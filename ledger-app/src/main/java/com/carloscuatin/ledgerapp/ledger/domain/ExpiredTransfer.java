package com.carloscuatin.ledgerapp.ledger.domain;

import java.util.Objects;

/**
 * A hold that ran out of time before it was posted; no money moved.
 */
public record ExpiredTransfer(TransferTerms terms) implements Transfer {

    public ExpiredTransfer {
        Objects.requireNonNull(terms, "terms");
    }
}
