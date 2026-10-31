package com.carloscuatin.ledgerapp.ledger.domain;

import java.util.Objects;

/**
 * A transfer whose money moved; the journal entry records that movement.
 */
public record PostedTransfer(TransferTerms terms, JournalEntry entry) implements Transfer {

    public PostedTransfer {
        Objects.requireNonNull(terms, "terms");
        Objects.requireNonNull(entry, "entry");
    }
}
