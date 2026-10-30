package com.carloscuatin.ledgerapp.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JournalEntryTest {

    private static final Currency COP = Currency.getInstance("COP");
    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency JPY = Currency.getInstance("JPY");
    private static final AccountId CASH = new AccountId(UUID.randomUUID());
    private static final AccountId WALLET = new AccountId(UUID.randomUUID());

    private static Posting debit(AccountId account, String amount) {
        return new Posting(account, Side.DEBIT, Money.parse(amount, COP));
    }

    private static Posting credit(AccountId account, String amount) {
        return new Posting(account, Side.CREDIT, Money.parse(amount, COP));
    }

    private static Posting debit(AccountId account, String amount, Currency currency) {
        return new Posting(account, Side.DEBIT, Money.parse(amount, currency));
    }

    private static Posting credit(AccountId account, String amount, Currency currency) {
        return new Posting(account, Side.CREDIT, Money.parse(amount, currency));
    }

    @Test
    void changingTheSourceListDoesNotChangeTheEntry() {
        var source = new ArrayList<Posting>();
        source.add(debit(CASH, "100.00"));
        source.add(credit(WALLET, "100.00"));
        var entry = new JournalEntry(source);

        source.add(debit(CASH, "5.00"));

        assertThat(entry.postings()).hasSize(2);
    }

    @Test
    void thePostingsOfAnEntryCannotBeModified() {
        var entry = new JournalEntry(new ArrayList<>(List.of(debit(CASH, "100.00"), credit(WALLET, "100.00"))));
        var postings = entry.postings();
        var extra = debit(CASH, "1.00");

        assertThatThrownBy(() -> postings.add(extra)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> postings.remove(0)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void postingsKeepTheirOrder() {
        var first = credit(WALLET, "60.00");
        var second = debit(CASH, "100.00");
        var third = credit(WALLET, "40.00");

        var entry = new JournalEntry(new ArrayList<>(List.of(first, second, third)));

        assertThat(entry.postings()).containsExactly(first, second, third);
    }

    @Test
    void aNullListOrANullPostingIsRejected() {
        var withANullPosting = Arrays.asList(debit(CASH, "100.00"), null);

        assertThatThrownBy(() -> new JournalEntry(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new JournalEntry(withANullPosting)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void anEmptyEntryIsRejected() {
        List<Posting> none = List.of();

        assertThatThrownBy(() -> new JournalEntry(none))
                .isInstanceOf(TooFewPostingsException.class)
                .hasMessage("A journal entry needs at least two postings, got 0");
    }

    @Test
    void aSinglePostingIsRejectedBeforeBalancing() {
        var single = List.of(debit(CASH, "100.00"));

        assertThatThrownBy(() -> new JournalEntry(single))
                .isInstanceOf(TooFewPostingsException.class)
                .hasMessage("A journal entry needs at least two postings, got 1");
    }

    @Test
    void aSimpleTwoPostingEntryBalances() {
        var postings = List.of(debit(CASH, "150000.00"), credit(WALLET, "150000.00"));

        assertThat(new JournalEntry(postings).postings()).containsExactlyElementsOf(postings);
    }

    @Test
    void oneDebitAgainstTwoCreditsBalances() {
        var fee = new AccountId(UUID.randomUUID());
        var postings = List.of(debit(CASH, "10100.00"), credit(WALLET, "10000.00"), credit(fee, "100.00"));

        assertThat(new JournalEntry(postings).postings()).containsExactlyElementsOf(postings);
    }

    @Test
    void eachCurrencyBalancesOnItsOwn() {
        var postings = List.of(
                debit(CASH, "100.00"),
                debit(CASH, "10.00", USD),
                credit(WALLET, "100.00"),
                credit(WALLET, "10.00", USD));

        assertThat(new JournalEntry(postings).postings()).containsExactlyElementsOf(postings);
    }

    @ParameterizedTest
    @MethodSource("unbalancedEntries")
    void anEntryThatDoesNotBalanceIsRejected(List<Posting> postings, String imbalance) {
        assertThatThrownBy(() -> new JournalEntry(postings))
                .isInstanceOf(UnbalancedEntryException.class)
                .hasMessage("Journal entry does not balance (debits minus credits): " + imbalance);
    }

    static Stream<Arguments> unbalancedEntries() {
        return Stream.of(
                Arguments.of(List.of(debit(CASH, "150000.00"), credit(WALLET, "149999.50")), "COP 0.50"),
                Arguments.of(List.of(debit(CASH, "149999.50"), credit(WALLET, "150000.00")), "COP -0.50"),
                Arguments.of(List.of(debit(CASH, "100.00"), debit(WALLET, "200.00")), "COP 300.00"),
                Arguments.of(List.of(credit(CASH, "100.00"), credit(WALLET, "200.00")), "COP -300.00"),
                Arguments.of(List.of(debit(CASH, "100.00"), credit(WALLET, "100.00", USD)), "COP 100.00, USD -100.00"),
                Arguments.of(List.of(debit(CASH, "100", JPY), credit(WALLET, "99", JPY)), "JPY 1"),
                Arguments.of(
                        List.of(
                                debit(CASH, "100.00"),
                                credit(WALLET, "100.00"),
                                debit(CASH, "10.00", USD),
                                credit(WALLET, "9.00", USD)),
                        "USD 1.00"),
                Arguments.of(
                        List.of(
                                debit(CASH, "10.00", USD),
                                credit(WALLET, "9.00", USD),
                                debit(CASH, "99.50"),
                                credit(WALLET, "100.00")),
                        "USD 1.00, COP -0.50"));
    }
}
