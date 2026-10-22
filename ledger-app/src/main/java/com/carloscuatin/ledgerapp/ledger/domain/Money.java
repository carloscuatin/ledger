package com.carloscuatin.ledgerapp.ledger.domain;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * A monetary amount stored as an exact count of its currency's minor units. The number of decimals always comes
 * from the currency, never from a constant, so any ISO 4217 currency with a defined scale is supported.
 */
public record Money(long minorUnits, Currency currency) {

    public Money {
        scaleOf(currency);
    }

    public static Money ofMinor(long minorUnits, Currency currency) {
        return new Money(minorUnits, currency);
    }

    public static Money zero(Currency currency) {
        return new Money(0, currency);
    }

    public static Money parse(String amount, Currency currency) {
        Objects.requireNonNull(amount, "amount");
        int scale = scaleOf(currency);
        if (!amount.matches("-?\\d+(\\.\\d+)?")) {
            throw new InvalidAmountException(amount, currency);
        }

        var decimal = new BigDecimal(amount);
        if (decimal.scale() > scale) {
            throw new InvalidAmountException(amount, currency);
        }

        try {
            return new Money(decimal.movePointRight(scale).longValueExact(), currency);
        } catch (ArithmeticException e) {
            throw new InvalidAmountException(amount, currency);
        }
    }

    public int scale() {
        return currency.getDefaultFractionDigits();
    }

    private static int scaleOf(Currency currency) {
        Objects.requireNonNull(currency, "currency");
        int digits = currency.getDefaultFractionDigits();
        if (digits < 0) {
            throw new IllegalArgumentException("Currency without a scale: " + currency.getCurrencyCode());
        }
        return digits;
    }
}
