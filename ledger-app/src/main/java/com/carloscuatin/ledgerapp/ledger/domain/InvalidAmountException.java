package com.carloscuatin.ledgerapp.ledger.domain;

import java.util.Currency;

public class InvalidAmountException extends DomainException {

    public InvalidAmountException(String amount, Currency currency) {
        super("Invalid amount '%s' for %s".formatted(amount, currency.getCurrencyCode()));
    }
}
