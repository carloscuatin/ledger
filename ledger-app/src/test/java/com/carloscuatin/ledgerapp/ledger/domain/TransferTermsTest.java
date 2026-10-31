package com.carloscuatin.ledgerapp.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Currency;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TransferTermsTest {

    private static final Currency COP = Currency.getInstance("COP");
    private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final AccountId SOURCE = new AccountId(SOURCE_ID);
    private static final AccountId DESTINATION = new AccountId(UUID.fromString("00000000-0000-7000-8000-000000000002"));

    @Test
    void termsKeepTheirAccountsAndAmount() {
        var amount = Money.parse("150000.00", COP);

        var terms = new TransferTerms(SOURCE, DESTINATION, amount);

        assertThat(terms.source()).isEqualTo(SOURCE);
        assertThat(terms.destination()).isEqualTo(DESTINATION);
        assertThat(terms.amount()).isEqualTo(amount);
    }

    @Test
    void theSmallestPositiveAmountIsAccepted() {
        var terms = new TransferTerms(SOURCE, DESTINATION, Money.ofMinor(1, COP));

        assertThat(terms.amount().minorUnits()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"0, Amount must be positive: COP 0.00", "-1, Amount must be positive: COP -0.01"})
    void nonPositiveAmountsAreRejected(long minorUnits, String expectedMessage) {
        var amount = Money.ofMinor(minorUnits, COP);

        assertThatThrownBy(() -> new TransferTerms(SOURCE, DESTINATION, amount))
                .isInstanceOf(NonPositiveAmountException.class)
                .hasMessage(expectedMessage);
    }

    @Test
    void aTransferToTheSameAccountIsRejected() {
        var amount = Money.ofMinor(100, COP);

        assertThatThrownBy(() -> new TransferTerms(SOURCE, SOURCE, amount))
                .isInstanceOf(SameAccountTransferException.class)
                .hasMessage("Source and destination must be different accounts: " + SOURCE_ID);
    }

    @Test
    void twoReferencesToTheSameAccountAreTheSameAccount() {
        var sameAccountAgain = new AccountId(UUID.fromString(SOURCE_ID.toString()));
        var amount = Money.ofMinor(100, COP);

        assertThatThrownBy(() -> new TransferTerms(SOURCE, sameAccountAgain, amount))
                .isInstanceOf(SameAccountTransferException.class);
    }

    @Test
    void theAmountIsCheckedBeforeTheAccounts() {
        var zero = Money.zero(COP);

        assertThatThrownBy(() -> new TransferTerms(SOURCE, SOURCE, zero))
                .isInstanceOf(NonPositiveAmountException.class);
    }

    @Test
    void nullTermsPartsAreRejected() {
        var amount = Money.ofMinor(1, COP);

        assertThatThrownBy(() -> new TransferTerms(null, DESTINATION, amount)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TransferTerms(SOURCE, null, amount)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TransferTerms(SOURCE, DESTINATION, null))
                .isInstanceOf(NullPointerException.class);
    }
}
