package com.carloscuatin.ledgerapp.ledger.domain;

import java.util.List;
import java.util.stream.Collectors;

/**
 * In double entry, debits equal credits in every currency. The message lists what is left over in each currency that
 * does not balance, debits minus credits.
 */
public class UnbalancedEntryException extends DomainException {

    public UnbalancedEntryException(List<Money> imbalances) {
        super("Journal entry does not balance (debits minus credits): "
                + imbalances.stream().map(Money::toString).collect(Collectors.joining(", ")));
    }
}
