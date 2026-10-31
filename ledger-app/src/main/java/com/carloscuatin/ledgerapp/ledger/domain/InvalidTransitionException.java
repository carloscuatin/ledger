package com.carloscuatin.ledgerapp.ledger.domain;

/**
 * Only a pending transfer can move; posted, voided and expired transfers are final.
 */
public class InvalidTransitionException extends DomainException {

    public InvalidTransitionException(TransferAction action, TransferStatus status) {
        super("Cannot " + action + " a transfer that is " + status);
    }
}
