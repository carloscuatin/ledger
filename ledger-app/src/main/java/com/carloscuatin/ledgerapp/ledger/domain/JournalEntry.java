package com.carloscuatin.ledgerapp.ledger.domain;

import java.util.List;

/**
 * A double-entry record of money moving between accounts. An entry never changes once created: a mistake is corrected
 * by another entry that reverses it.
 */
public record JournalEntry(List<Posting> postings) {

    public JournalEntry {
        // A copy, not a read-only view: a view would still follow later changes to the caller's list. The copy also
        // rejects a null list or a null posting.
        postings = List.copyOf(postings);
    }
}
