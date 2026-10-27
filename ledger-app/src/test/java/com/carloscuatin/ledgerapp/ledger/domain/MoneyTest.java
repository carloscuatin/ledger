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
        assertThatThrownBy(() -> Money.parse("10", currency)).isInstanceOf(IllegalArgumentException.class);
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

        assertThat(money.minorUnits()).isZero();
        assertThat(money.currency()).isEqualTo(jpy);
    }

    @Test
    void sameMinorUnitsInAnotherCurrencyAreNotEqual() {
        var money = new Money(15000, COP);
        var other = new Money(15000, USD);

        assertThat(money).isEqualTo(new Money(15000, COP)).isNotEqualTo(other);
    }

    @Test
    void bothErrorsShareTheDomainBaseType() {
        var mismatch = new CurrencyMismatchException(COP, USD);
        var invalid = new InvalidAmountException("1.005", COP);

        assertThat(mismatch).isInstanceOf(DomainException.class).isInstanceOf(RuntimeException.class);
        assertThat(invalid).isInstanceOf(DomainException.class).isInstanceOf(RuntimeException.class);
    }

    @ParameterizedTest
    @CsvSource({
        "150000.00, COP, 15000000",
        "150000, COP, 15000000",
        "10.0, COP, 1000",
        "10.00, COP, 1000",
        "100, JPY, 100",
        "1.234, BHD, 1234",
        "010, COP, 1000",
        "-0, COP, 0",
        "-0.00, COP, 0",
        "-150.00, COP, -15000",
        "92233720368547758.07, COP, 9223372036854775807"
    })
    void validAmountsAreParsed(String text, String currencyCode, long expectedMinorUnits) {
        var currency = Currency.getInstance(currencyCode);
        var money = Money.parse(text, currency);

        assertThat(money.minorUnits()).isEqualTo(expectedMinorUnits);
        assertThat(money.currency()).isEqualTo(currency);
    }

    @Test
    void parsedAndMinorUnitsAreTheSameMoney() {
        var money = Money.parse("150000.00", COP);
        var other = Money.ofMinor(15000000, COP);

        assertThat(money).isEqualTo(other);
    }

    @ParameterizedTest
    @CsvSource({"1.005, COP", "1.5, JPY"})
    void amountsWithMoreDecimalsThanTheCurrencyAreRejected(String text, String currencyCode) {
        var currency = Currency.getInstance(currencyCode);

        assertThatThrownBy(() -> Money.parse(text, currency)).isInstanceOf(InvalidAmountException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1E+5", "+10", "1,000.00", " 10", "", "10.", ".5", "--1", "10\n", "\u0661\u0660"})
    void malformedAmountsAreRejected(String text) {
        assertThatThrownBy(() -> Money.parse(text, COP)).isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void anAmountBeyondTheLongRangeIsRejected() {
        assertThatThrownBy(() -> Money.parse("92233720368547758.08", COP)).isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void aNullAmountIsRejected() {
        assertThatThrownBy(() -> Money.parse(null, COP))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("amount");
    }

    @Test
    void addingInTheSameCurrency() {
        var sum = Money.parse("150000.00", COP).plus(Money.parse("0.50", COP));

        assertThat(sum).isEqualTo(Money.ofMinor(15000050, COP));
    }

    @Test
    void subtractingInTheSameCurrency() {
        var difference = Money.parse("10.00", COP).minus(Money.parse("0.50", COP));

        assertThat(difference).isEqualTo(Money.ofMinor(950, COP));
    }

    @Test
    void operationAcrossCurrenciesIsRejected() {
        var cop = Money.parse("150000.00", COP);
        var usd = Money.parse("150000.00", USD);

        assertThatThrownBy(() -> cop.plus(usd)).isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> cop.minus(usd)).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void arithmeticOverflowIsASystemError() {
        var max = Money.ofMinor(Long.MAX_VALUE, COP);
        var min = Money.ofMinor(Long.MIN_VALUE, COP);
        var one = Money.ofMinor(1, COP);

        assertThatThrownBy(() -> max.plus(one))
                .isInstanceOf(ArithmeticException.class)
                .isNotInstanceOf(DomainException.class);
        assertThatThrownBy(() -> min.minus(one))
                .isInstanceOf(ArithmeticException.class)
                .isNotInstanceOf(DomainException.class);
    }

    @Test
    void negatingAnAmount() {
        var money = Money.ofMinor(15000, COP);

        assertThat(money.negate()).isEqualTo(Money.ofMinor(-15000, COP));
        assertThat(money.negate().negate()).isEqualTo(money);
    }

    @Test
    void negatingTheMinimumOverflows() {
        var min = Money.ofMinor(Long.MIN_VALUE, COP);

        assertThatThrownBy(() -> min.negate())
                .isInstanceOf(ArithmeticException.class)
                .isNotInstanceOf(DomainException.class);
    }
}
