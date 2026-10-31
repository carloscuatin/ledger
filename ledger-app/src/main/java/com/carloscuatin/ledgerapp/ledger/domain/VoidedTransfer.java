package com.carloscuatin.ledgerapp.ledger.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * A hold released on purpose before it was posted; no money moved.
 */
public record VoidedTransfer(TransferTerms terms) implements Transfer {

    public VoidedTransfer {
        Objects.requireNonNull(terms, "terms");
    }

    @Override
    public Optional<JournalEntry> journalEntry() {
        return Optional.empty();
    }
}
