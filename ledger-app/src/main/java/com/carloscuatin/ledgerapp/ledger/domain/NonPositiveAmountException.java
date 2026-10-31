package com.carloscuatin.ledgerapp.ledger.domain;

/**
 * An amount that moves money must be positive: the direction of the movement is carried elsewhere, by the side of a
 * posting or by the accounts of a transfer.
 */
public class NonPositiveAmountException extends DomainException {

    public NonPositiveAmountException(Money amount) {
        super("Amount must be positive: " + amount);
    }
}
