package com.carloscuatin.ledgerapp.ledger.domain;

import java.util.List;
import java.util.StringJoiner;

/**
 * In double entry, debits equal credits in every currency. The message lists what is left over in each currency that
 * does not balance, debits minus credits.
 */
public class UnbalancedEntryException extends DomainException {

    public UnbalancedEntryException(List<Money> imbalances) {
        super("Journal entry does not balance (debits minus credits): " + describe(imbalances));
    }

    private static String describe(List<Money> imbalances) {
        var joiner = new StringJoiner(", ");
        for (var imbalance : imbalances) {
            joiner.add(imbalance.toString());
        }
        return joiner.toString();
    }
}
