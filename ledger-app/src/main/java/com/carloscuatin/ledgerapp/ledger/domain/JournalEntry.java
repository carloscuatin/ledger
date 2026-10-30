package com.carloscuatin.ledgerapp.ledger.domain;

import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A double-entry record of money moving between accounts. An entry never changes once created: a mistake is corrected
 * by another entry that reverses it.
 */
public record JournalEntry(List<Posting> postings) {

    public JournalEntry {
        // A copy, not a read-only view: a view would still follow later changes to the caller's list. The copy also
        // rejects a null list or a null posting.
        postings = List.copyOf(postings);
        if (postings.size() < 2) {
            throw new TooFewPostingsException(postings.size());
        }

        var imbalances = imbalances(postings);
        if (!imbalances.isEmpty()) {
            throw new UnbalancedEntryException(imbalances);
        }
    }

    /**
     * The net of each currency that does not add up to zero. Summing within a currency means amounts in different
     * currencies never offset each other, and insertion order keeps currencies in the order they first appear.
     */
    private static List<Money> imbalances(List<Posting> postings) {
        Map<Currency, Money> netByCurrency = postings.stream()
                .map(Posting::signedAmount)
                .collect(Collectors.toMap(Money::currency, net -> net, Money::plus, LinkedHashMap::new));
        return netByCurrency.values().stream().filter(net -> !net.isZero()).toList();
    }
}
