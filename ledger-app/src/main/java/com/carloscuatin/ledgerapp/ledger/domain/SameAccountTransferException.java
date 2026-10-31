package com.carloscuatin.ledgerapp.ledger.domain;

/**
 * A transfer moves money between two different accounts; moving it from an account to itself would change nothing
 * and only hide a mistake by the caller.
 */
public class SameAccountTransferException extends DomainException {

    public SameAccountTransferException(AccountId account) {
        super("Source and destination must be different accounts: " + account.value());
    }
}
