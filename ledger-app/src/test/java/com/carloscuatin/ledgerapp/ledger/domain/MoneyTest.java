package com.carloscuatin.ledgerapp.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Currency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

    private static final Currency COP = Currency.getInstance("COP");
    private static final Currency USD = Currency.getInstance("USD");

    @ParameterizedTest
    @CsvSource({"COP, 2", "JPY, 0", "BHD, 3"})
    void currenciesWithAScaleAreAccepted(String code, int expectedScale) {
        var money = new Money(1, Currency.getInstance(code));

        assertThat(money.minorUnits()).isEqualTo(1);
        assertThat(money.scale()).isEqualTo(expectedScale);
    }

    @ParameterizedTest
    @ValueSource(strings = {"XAU", "XXX"})
    void currenciesWithoutAScaleAreRejected(String code) {
        var currency = Currency.getInstance(code);

        assertThatThrownBy(() -> new Money(1, currency)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aNullCurrencyIsRejected() {
        assertThatThrownBy(() -> new Money(1, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Money.ofMinor(1, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Money.zero(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void negativeAmountsAreAllowed() {
        var money = new Money(-15000, COP);

        assertThat(money.minorUnits()).isEqualTo(-15000);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, -1, Long.MAX_VALUE, Long.MIN_VALUE})
    void minorUnitsAreKept(long units) {
        var money = Money.ofMinor(units, COP);

        assertThat(money.minorUnits()).isEqualTo(units);
        assertThat(money.currency()).isEqualTo(COP);
    }

    @Test
    void zeroOfACurrency() {
        var jpy = Currency.getInstance("JPY");
        var money = Money.zero(jpy);

        assertThat(money.minorUnits()).isEqualTo(0);
        assertThat(money.currency()).isEqualTo(jpy);
    }

    @Test
    void sameMinorUnitsInAnotherCurrencyAreNotEqual() {
        var money = new Money(15000, COP);
        var other = new Money(15000, USD);

        assertThat(money).isEqualTo(new Money(15000, COP));
        assertThat(money).isNotEqualTo(other);
    }

    @Test
    void bothErrorsShareTheDomainBaseType() {
        var mismatch = new CurrencyMismatchException(COP, USD);
        var invalid = new InvalidAmountException("1.005", COP);

        assertThat(mismatch).isInstanceOf(DomainException.class).isInstanceOf(RuntimeException.class);
        assertThat(invalid).isInstanceOf(DomainException.class).isInstanceOf(RuntimeException.class);
    }
}
