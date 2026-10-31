package com.carloscuatin.ledgerapp.ledger.domain;

import java.util.Objects;

/**
 * One line of a journal entry: an amount moved on one side of an account. The side gives the direction, so the amount
 * is always positive.
 */
public record Posting(AccountId account, Side side, Money amount) {

    public Posting {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(amount, "amount");
        Amounts.requirePositive(amount);
    }

    /** The amount with the sign of its side, so the postings of a balanced entry add up to zero. */
    Money signedAmount() {
        return switch (side) {
            case DEBIT -> amount;
            case CREDIT -> amount.negate();
        };
    }
}
