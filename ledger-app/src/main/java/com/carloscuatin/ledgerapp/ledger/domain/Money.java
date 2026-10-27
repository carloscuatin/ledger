package com.carloscuatin.ledgerapp.ledger.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        } catch (ArithmeticException _) {
            throw new InvalidAmountException(amount, currency);
        }
    }

    public int scale() {
        return currency.getDefaultFractionDigits();
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(minorUnits, other.minorUnits), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(minorUnits, other.minorUnits), currency);
    }

    public Money negate() {
        return new Money(Math.negateExact(minorUnits), currency);
    }

    public Money applyRate(BigDecimal rate) {
        Objects.requireNonNull(rate, "rate");
        var exact = BigDecimal.valueOf(minorUnits).multiply(rate);
        var rounded = exact.setScale(0, RoundingMode.HALF_EVEN);

        return new Money(rounded.longValueExact(), currency);
    }

    private static int scaleOf(Currency currency) {
        Objects.requireNonNull(currency, "currency");
        int digits = currency.getDefaultFractionDigits();
        if (digits < 0) {
            throw new IllegalArgumentException("Currency without a scale: " + currency.getCurrencyCode());
        }
        return digits;
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }
}
