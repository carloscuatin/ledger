package com.carloscuatin.ledgerapp.ledger.domain;

import java.util.Objects;

/**
 * What a transfer moves, from which account to which: the part every state of a transfer shares, validated once.
 */
public record TransferTerms(AccountId source, AccountId destination, Money amount) {

    public TransferTerms {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(amount, "amount");
        Amounts.requirePositive(amount);
        // equals, not ==: two references built from the same identifier are the same account.
        if (source.equals(destination)) {
            throw new SameAccountTransferException(source);
        }
    }
}
