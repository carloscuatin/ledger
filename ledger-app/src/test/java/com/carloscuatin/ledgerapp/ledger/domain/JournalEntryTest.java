package com.carloscuatin.ledgerapp.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class JournalEntryTest {

    private static final Currency COP = Currency.getInstance("COP");
    private static final AccountId CASH = new AccountId(UUID.randomUUID());
    private static final AccountId WALLET = new AccountId(UUID.randomUUID());

    private static Posting debit(AccountId account, String amount) {
        return new Posting(account, Side.DEBIT, Money.parse(amount, COP));
    }

    private static Posting credit(AccountId account, String amount) {
        return new Posting(account, Side.CREDIT, Money.parse(amount, COP));
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
}
