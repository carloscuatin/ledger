package com.carloscuatin.ledgerapp.ledger.domain;

import java.util.Currency;

public class CurrencyMismatchException extends DomainException {

    public CurrencyMismatchException(Currency left, Currency right) {
        super("Cannot operate %s with %s".formatted(left.getCurrencyCode(), right.getCurrencyCode()));
    }
}
