package com.carloscuatin.ledgerapp.ledger.domain;

/**
 * A posting amount must be positive because its side already carries the direction of the movement.
 */
public class NonPositiveAmountException extends DomainException {

    public NonPositiveAmountException(Money amount) {
        super("Amount must be positive: " + amount);
    }
}
