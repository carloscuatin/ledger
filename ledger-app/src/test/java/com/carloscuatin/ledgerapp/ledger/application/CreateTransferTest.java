package com.carloscuatin.ledgerapp.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Currency;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.carloscuatin.ledgerapp.ledger.domain.AccountId;
import com.carloscuatin.ledgerapp.ledger.domain.CurrencyNotEnabledException;
import com.carloscuatin.ledgerapp.ledger.domain.DomainException;
import com.carloscuatin.ledgerapp.ledger.domain.Money;
import com.carloscuatin.ledgerapp.ledger.domain.PendingTransfer;
import com.carloscuatin.ledgerapp.ledger.domain.TransferStatus;
import com.carloscuatin.ledgerapp.ledger.domain.TransferTerms;

class CreateTransferTest {

    private static final Currency COP = Currency.getInstance("COP");
    private static final Currency USD = Currency.getInstance("USD");
    private static final AccountId SOURCE = new AccountId(UUID.fromString("00000000-0000-7000-8000-000000000001"));
    private static final AccountId DESTINATION = new AccountId(UUID.fromString("00000000-0000-7000-8000-000000000002"));

    private static TransferTerms terms(String amount, Currency currency) {
        return new TransferTerms(SOURCE, DESTINATION, Money.parse(amount, currency));
    }

    @Test
    void anEnabledCurrencyCreatesAHold() {
        var createTransfer = new CreateTransfer(Set.of(COP));
        var terms = terms("150000.00", COP);

        PendingTransfer hold = createTransfer.create(terms);

        assertThat(hold.status()).isEqualTo(TransferStatus.PENDING);
        assertThat(hold.terms()).isEqualTo(terms);
    }

    @Test
    void aCurrencyThatIsNotEnabledIsRejected() {
        var createTransfer = new CreateTransfer(Set.of(COP));
        var dollars = terms("100.00", USD);

        assertThatThrownBy(() -> createTransfer.create(dollars))
                .isInstanceOf(CurrencyNotEnabledException.class)
                .isInstanceOf(DomainException.class)
                .hasMessage("Currency not enabled: USD");
    }

    @Test
    void everyEnabledCurrencyCreatesAHold() {
        var createTransfer = new CreateTransfer(Set.of(COP, USD));
        var dollars = terms("100.00", USD);

        assertThat(createTransfer.create(dollars).terms()).isEqualTo(dollars);
    }

    @Test
    void nullTermsAreRejected() {
        var createTransfer = new CreateTransfer(Set.of(COP));

        assertThatThrownBy(() -> createTransfer.create(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void theEnabledCurrenciesAreFixedWhenTheUseCaseIsBuilt() {
        var enabled = new HashSet<Currency>();
        enabled.add(COP);
        var createTransfer = new CreateTransfer(enabled);
        var dollars = terms("100.00", USD);

        enabled.add(USD);

        assertThatThrownBy(() -> createTransfer.create(dollars)).isInstanceOf(CurrencyNotEnabledException.class);
    }
}
