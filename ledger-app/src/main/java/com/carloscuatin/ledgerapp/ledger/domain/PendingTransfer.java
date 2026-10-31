package com.carloscuatin.ledgerapp.ledger.domain;

import java.util.Objects;

/**
 * A hold: the money is reserved and waits to be posted, voided or expired.
 */
public record PendingTransfer(TransferTerms terms) implements Transfer {

    public PendingTransfer {
        Objects.requireNonNull(terms, "terms");
    }
}
