package com.carloscuatin.ledgerapp.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Currency;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PostingTest {

    private static final Currency COP = Currency.getInstance("COP");
    private static final AccountId ACCOUNT = new AccountId(UUID.randomUUID());

    @Test
    void bothSidesKeepTheirAccountSideAndAmount() {
        var amount = Money.parse("150000.00", COP);

        var debit = new Posting(ACCOUNT, Side.DEBIT, amount);
        var credit = new Posting(ACCOUNT, Side.CREDIT, amount);

        assertThat(debit.account()).isEqualTo(ACCOUNT);
        assertThat(debit.side()).isEqualTo(Side.DEBIT);
        assertThat(debit.amount()).isEqualTo(amount);
        assertThat(credit.side()).isEqualTo(Side.CREDIT);
        assertThat(credit.amount()).isEqualTo(amount);
    }

    @Test
    void theSmallestPositiveAmountIsAccepted() {
        var posting = new Posting(ACCOUNT, Side.DEBIT, Money.ofMinor(1, COP));

        assertThat(posting.amount().minorUnits()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"0, Amount must be positive: COP 0.00", "-1, Amount must be positive: COP -0.01"})
    void nonPositiveAmountsAreRejected(long minorUnits, String expectedMessage) {
        var amount = Money.ofMinor(minorUnits, COP);

        assertThatThrownBy(() -> new Posting(ACCOUNT, Side.DEBIT, amount))
                .isInstanceOf(NonPositiveAmountException.class)
                .hasMessage(expectedMessage);
    }

    @Test
    void nullPostingPartsAreRejected() {
        var amount = Money.ofMinor(1, COP);

        assertThatThrownBy(() -> new Posting(null, Side.DEBIT, amount)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Posting(ACCOUNT, null, amount)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Posting(ACCOUNT, Side.DEBIT, null))
                .isInstanceOf(NullPointerException.class);
    }
}
