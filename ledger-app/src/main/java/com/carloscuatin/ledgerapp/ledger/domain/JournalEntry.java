package com.carloscuatin.ledgerapp.ledger.domain;

import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;

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

        // Insertion order keeps the error message in the order currencies first appear.
        var netByCurrency = new LinkedHashMap<Currency, Money>();
        for (var posting : postings) {
            var signed = posting.signedAmount();
            netByCurrency.merge(signed.currency(), signed, Money::plus);
        }
        var imbalances = new ArrayList<Money>();
        for (var net : netByCurrency.values()) {
            if (!net.isZero()) {
                imbalances.add(net);
            }
        }
        if (!imbalances.isEmpty()) {
            throw new UnbalancedEntryException(imbalances);
        }
    }
}
