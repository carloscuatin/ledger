package com.carloscuatin.ledgerapp.ledger.domain;

import java.util.Currency;

/**
 * A currency the JDK knows but that has no minor units, so no amount can be expressed in it. Whose mistake that is
 * depends on where the currency came from, which only the edge knows.
 */
public class CurrencyWithoutScaleException extends DomainException {

    public CurrencyWithoutScaleException(Currency currency) {
        super("Currency without a scale: " + currency.getCurrencyCode());
    }
}
