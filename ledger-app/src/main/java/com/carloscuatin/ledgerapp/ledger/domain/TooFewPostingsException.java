package com.carloscuatin.ledgerapp.ledger.domain;

/**
 * A journal entry needs at least two postings: money always leaves one account and reaches another.
 */
public class TooFewPostingsException extends DomainException {

    public TooFewPostingsException(int count) {
        super("A journal entry needs at least two postings, got " + count);
    }
}
