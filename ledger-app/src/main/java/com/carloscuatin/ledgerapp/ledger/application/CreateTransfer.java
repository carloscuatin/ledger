package com.carloscuatin.ledgerapp.ledger.application;

import java.util.Currency;
import java.util.Objects;
import java.util.Set;

import org.springframework.transaction.annotation.Transactional;

import com.carloscuatin.ledgerapp.ledger.domain.CurrencyNotEnabledException;
import com.carloscuatin.ledgerapp.ledger.domain.PendingTransfer;
import com.carloscuatin.ledgerapp.ledger.domain.TransferId;
import com.carloscuatin.ledgerapp.ledger.domain.TransferTerms;

/**
 * Opens a transfer as a hold, in one of the currencies this deployment operates, and stores it in one transaction. It
 * receives everything it needs through its constructor and knows nothing of the container that builds it. The class is
 * not final because the transaction boundary is applied by a generated subclass.
 */
public class CreateTransfer {

    private final Set<Currency> enabledCurrencies;
    private final IdGenerator ids;
    private final Transfers transfers;

    public CreateTransfer(Set<Currency> enabledCurrencies, IdGenerator ids, Transfers transfers) {
        // A copy: the caller's collection cannot widen or narrow what this use case accepts after it is built.
        this.enabledCurrencies = Set.copyOf(Objects.requireNonNull(enabledCurrencies, "enabledCurrencies"));
        this.ids = Objects.requireNonNull(ids, "ids");
        this.transfers = Objects.requireNonNull(transfers, "transfers");
    }

    @Transactional
    public PendingTransfer create(TransferTerms terms) {
        Objects.requireNonNull(terms, "terms");
        var currency = terms.amount().currency();
        if (!enabledCurrencies.contains(currency)) {
            throw new CurrencyNotEnabledException(currency);
        }
        var hold = new PendingTransfer(new TransferId(ids.next()), terms);
        transfers.add(hold);
        return hold;
    }
}
