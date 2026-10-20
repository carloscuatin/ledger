package com.carloscuatin.ledgerapp.ledger.domain;

/** Base type for business rule violations, so the edge can translate them in one place. */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
