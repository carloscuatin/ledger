package com.carloscuatin.ledgerapp.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
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
        assertThatThrownBy(() -> Money.parse("1", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void negativeAmountsAreAllowed() {
        var money = new Money(-15000, COP);

        assertThat(money.minorUnits()).isEqualTo(-15000);
        assertThat(money.isNegative()).isTrue();
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
        assertThat(money.isZero()).isTrue();
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
    @CsvSource({"1.005, COP", "1.5, JPY", "1.000, COP", "1.0, JPY"})
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

        assertThatThrownBy(min::negate).isInstanceOf(ArithmeticException.class).isNotInstanceOf(DomainException.class);
    }

    @ParameterizedTest
    @CsvSource({
        "10001.00, COP, 0.015, 15002",
        "6001.00, COP, 0.025, 15002",
        "6001.40, COP, 0.025, 15004",
        "-6001.00, COP, 0.025, -15002",
        "-6001.40, COP, 0.025, -15004",
        "100, JPY, 0.015, 2",
        "300, JPY, 0.015, 4",
        "1.235, BHD, 0.5, 618",
        "10001.00, COP, 0, 0",
        "10001.00, COP, -0.015, -15002"
    })
    void aRateRoundsHalfEvenToTheCurrencyScale(
            String amount, String currencyCode, String rate, long expectedMinorUnits) {
        var currency = Currency.getInstance(currencyCode);
        var result = Money.parse(amount, currency).applyRate(new BigDecimal(rate));

        assertThat(result).isEqualTo(Money.ofMinor(expectedMinorUnits, currency));
    }

    @Test
    void aRateBuiltFromADoubleIsNotExact() {
        assertThat(new BigDecimal(0.015)).isNotEqualByComparingTo(new BigDecimal("0.015"));
    }

    @Test
    void aRateResultBeyondTheRangeIsASystemError() {
        var max = Money.ofMinor(Long.MAX_VALUE, COP);
        var rate = new BigDecimal("2");

        assertThatThrownBy(() -> max.applyRate(rate))
                .isInstanceOf(ArithmeticException.class)
                .isNotInstanceOf(DomainException.class);
    }

    @Test
    void aNullRateIsRejected() {
        var zero = Money.zero(COP);

        assertThatThrownBy(() -> zero.applyRate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("rate");
    }

    @ParameterizedTest
    @CsvSource({"9.99, true", "10.00, true", "10.01, false"})
    void greaterThanOrEqualWithinACurrency(String otherAmount, boolean expected) {
        var money = Money.parse("10.00", COP);
        var other = Money.parse(otherAmount, COP);

        assertThat(money.isGreaterThanOrEqualTo(other)).isEqualTo(expected);
    }

    @Test
    void comparingAcrossCurrenciesIsRejected() {
        var cop = Money.parse("1.00", COP);
        var usd = Money.parse("1.00", USD);

        assertThatThrownBy(() -> cop.isGreaterThanOrEqualTo(usd)).isInstanceOf(CurrencyMismatchException.class);
    }

    @ParameterizedTest
    @CsvSource({"-1, true, false", "0, false, true", "1, false, false"})
    void signPredicates(long minorUnits, boolean expectedNegative, boolean expectedZero) {
        var money = Money.ofMinor(minorUnits, COP);

        assertThat(money.isNegative()).isEqualTo(expectedNegative);
        assertThat(money.isZero()).isEqualTo(expectedZero);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0", "-0.00"})
    void negativeZeroIsZero(String text) {
        var money = Money.parse(text, COP);

        assertThat(money.isZero()).isTrue();
        assertThat(money.isNegative()).isFalse();
        assertThat(money).isEqualTo(Money.zero(COP));
    }

    @ParameterizedTest
    @CsvSource({
        "15000000, COP, 150000.00",
        "100, JPY, 100",
        "1234, BHD, 1.234",
        "-50, COP, -0.50",
        "0, COP, 0.00",
        "9223372036854775807, COP, 92233720368547758.07"
    })
    void decimalRepresentationHasExactlyTheCurrencyScale(long minorUnits, String currencyCode, String expected) {
        var money = Money.ofMinor(minorUnits, Currency.getInstance(currencyCode));

        assertThat(money.toDecimalString()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"15000000, COP, COP 150000.00", "-15000, COP, COP -150.00", "100, JPY, JPY 100"})
    void readableRepresentationIsTheCodeAndTheDecimal(long minorUnits, String currencyCode, String expected) {
        var money = Money.ofMinor(minorUnits, Currency.getInstance(currencyCode));

        assertThat(money).hasToString(expected);
    }
}
