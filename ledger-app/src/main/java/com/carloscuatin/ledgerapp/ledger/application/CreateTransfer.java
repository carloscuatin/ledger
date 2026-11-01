package com.carloscuatin.ledgerapp.ledger.application;

import java.util.Currency;
import java.util.Objects;
import java.util.Set;

import com.carloscuatin.ledgerapp.ledger.domain.CurrencyNotEnabledException;
import com.carloscuatin.ledgerapp.ledger.domain.PendingTransfer;
import com.carloscuatin.ledgerapp.ledger.domain.TransferTerms;

/**
 * Opens a transfer as a hold, in one of the currencies this deployment operates. It receives everything it needs
 * through its constructor and knows nothing of the container that builds it.
 */
public final class CreateTransfer {

    private final Set<Currency> enabledCurrencies;

    public CreateTransfer(Set<Currency> enabledCurrencies) {
        // A copy: the caller's collection cannot widen or narrow what this use case accepts after it is built.
        this.enabledCurrencies = Set.copyOf(Objects.requireNonNull(enabledCurrencies, "enabledCurrencies"));
    }

    public PendingTransfer create(TransferTerms terms) {
        Objects.requireNonNull(terms, "terms");
        var currency = terms.amount().currency();
        if (!enabledCurrencies.contains(currency)) {
            throw new CurrencyNotEnabledException(currency);
        }
        return new PendingTransfer(terms);
    }
}
