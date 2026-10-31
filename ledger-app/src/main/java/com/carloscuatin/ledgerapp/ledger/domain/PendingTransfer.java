package com.carloscuatin.ledgerapp.ledger.domain;

import java.util.List;
import java.util.Objects;

/**
 * A hold: the money is reserved and waits to be posted, voided or expired.
 */
public record PendingTransfer(TransferTerms terms) implements Transfer {

    public PendingTransfer {
        Objects.requireNonNull(terms, "terms");
    }

    // Each transition returns the exact type of the state it reaches and only a hold has them, so asking a posted,
    // voided or expired transfer to move again does not compile. The hold itself never changes.

    /** Moves the money: the source is debited and the destination credited for the held amount. */
    public PostedTransfer post() {
        var entry = new JournalEntry(List.of(
                new Posting(terms.source(), Side.DEBIT, terms.amount()),
                new Posting(terms.destination(), Side.CREDIT, terms.amount())));
        return new PostedTransfer(terms, entry);
    }

    public VoidedTransfer voidHold() {
        return new VoidedTransfer(terms);
    }

    public ExpiredTransfer expire() {
        return new ExpiredTransfer(terms);
    }
}
