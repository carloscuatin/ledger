package com.carloscuatin.ledgerapp.ledger.domain;

import java.util.Currency;
import java.util.Objects;

/**
 * A monetary amount stored as an exact count of its currency's minor units. The number of decimals always comes
 * from the currency, never from a constant, so any ISO 4217 currency with a defined scale is supported.
 */
public record Money(long minorUnits, Currency currency) {

    public Money {
        Objects.requireNonNull(currency, "currency");
        if (currency.getDefaultFractionDigits() < 0) {
            throw new IllegalArgumentException("Currency without a scale: " + currency.getCurrencyCode());
        }
    }

    public static Money ofMinor(long minorUnits, Currency currency) {
        return new Money(minorUnits, currency);
    }

    public static Money zero(Currency currency) {
        return new Money(0, currency);
    }

    public int scale() {
        return currency.getDefaultFractionDigits();
    }
}
