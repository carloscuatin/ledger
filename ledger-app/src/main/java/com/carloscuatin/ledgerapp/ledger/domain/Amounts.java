package com.carloscuatin.ledgerapp.ledger.domain;

/**
 * Rules shared by the amounts that move money.
 */
final class Amounts {

    private Amounts() {
    }

    /**
     * An amount that moves money must be positive: the direction of the movement is carried by the side of a posting or
     * by the accounts of a transfer.
     */
    static void requirePositive(Money amount) {
        if (amount.isZero() || amount.isNegative()) {
            throw new NonPositiveAmountException(amount);
        }
    }
}
