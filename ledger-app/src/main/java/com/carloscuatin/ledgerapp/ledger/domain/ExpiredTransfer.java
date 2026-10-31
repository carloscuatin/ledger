package com.carloscuatin.ledgerapp.ledger.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * A hold that ran out of time before it was posted; no money moved.
 */
public record ExpiredTransfer(TransferTerms terms) implements Transfer {

    public ExpiredTransfer {
        Objects.requireNonNull(terms, "terms");
    }

    @Override
    public Optional<JournalEntry> journalEntry() {
        return Optional.empty();
    }
}
