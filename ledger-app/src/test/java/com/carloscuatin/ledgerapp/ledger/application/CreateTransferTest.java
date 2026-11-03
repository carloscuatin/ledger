package com.carloscuatin.ledgerapp.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import com.carloscuatin.ledgerapp.ledger.domain.TransferId;
import com.carloscuatin.ledgerapp.ledger.domain.TransferStatus;
import com.carloscuatin.ledgerapp.ledger.domain.TransferTerms;

/**
 * The ports are mocks: these tests need no container, no database and no network, they decide what the identifier port
 * answers, and they see what is asked to be stored.
 */
class CreateTransferTest {

    private static final Currency COP = Currency.getInstance("COP");
    private static final Currency USD = Currency.getInstance("USD");
    private static final AccountId SOURCE = new AccountId(UUID.fromString("00000000-0000-7000-8000-000000000001"));
    private static final AccountId DESTINATION = new AccountId(UUID.fromString("00000000-0000-7000-8000-000000000002"));

    private static final UUID NEW_ID = UUID.fromString("00000000-0000-7000-8000-000000000003");

    private final IdGenerator ids = mock(IdGenerator.class);
    private final Transfers transfers = mock(Transfers.class);

    private static TransferTerms terms(String amount, Currency currency) {
        return new TransferTerms(SOURCE, DESTINATION, Money.parse(amount, currency));
    }

    @Test
    void anEnabledCurrencyCreatesAHold() {
        when(ids.next()).thenReturn(NEW_ID);
        var createTransfer = new CreateTransfer(Set.of(COP), ids, transfers);
        var terms = terms("150000.00", COP);

        PendingTransfer hold = createTransfer.create(terms);

        assertThat(hold.status()).isEqualTo(TransferStatus.PENDING);
        assertThat(hold.id()).isEqualTo(new TransferId(NEW_ID));
        assertThat(hold.terms()).isEqualTo(terms);
        verify(transfers).add(same(hold));
    }

    @Test
    void theIdentifierIsAskedForBeforeTheHoldIsStored() {
        when(ids.next()).thenReturn(NEW_ID);
        var createTransfer = new CreateTransfer(Set.of(COP), ids, transfers);

        var hold = createTransfer.create(terms("150000.00", COP));

        var order = inOrder(ids, transfers);
        order.verify(ids).next();
        order.verify(transfers).add(same(hold));
    }

    @Test
    void aCurrencyThatIsNotEnabledIsRejected() {
        var createTransfer = new CreateTransfer(Set.of(COP), ids, transfers);
        var dollars = terms("100.00", USD);

        assertThatThrownBy(() -> createTransfer.create(dollars))
                .isInstanceOf(CurrencyNotEnabledException.class)
                .isInstanceOf(DomainException.class)
                .hasMessage("Currency not enabled: USD");
        verify(transfers, never()).add(any());
        verify(ids, never()).next();
    }

    @Test
    void everyEnabledCurrencyCreatesAHold() {
        when(ids.next()).thenReturn(NEW_ID);
        var createTransfer = new CreateTransfer(Set.of(COP, USD), ids, transfers);
        var dollars = terms("100.00", USD);

        assertThat(createTransfer.create(dollars).terms()).isEqualTo(dollars);
    }

    @Test
    void nullTermsAreRejected() {
        var createTransfer = new CreateTransfer(Set.of(COP), ids, transfers);

        assertThatThrownBy(() -> createTransfer.create(null)).isInstanceOf(NullPointerException.class);
        verifyNoInteractions(ids, transfers);
    }

    @Test
    void nullEnabledCurrenciesAreRejected() {
        assertThatThrownBy(() -> new CreateTransfer(null, ids, transfers)).isInstanceOf(NullPointerException.class)
                .hasMessage("enabledCurrencies");
    }

    @Test
    void aNullIdGeneratorIsRejected() {
        assertThatThrownBy(() -> new CreateTransfer(Set.of(COP), null, transfers))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("ids");
    }

    @Test
    void aNullTransfersPortIsRejected() {
        assertThatThrownBy(() -> new CreateTransfer(Set.of(COP), ids, null)).isInstanceOf(NullPointerException.class)
                .hasMessage("transfers");
    }

    @Test
    void theEnabledCurrenciesAreFixedWhenTheUseCaseIsBuilt() {
        var enabled = new HashSet<Currency>();
        enabled.add(COP);
        var createTransfer = new CreateTransfer(enabled, ids, transfers);
        var dollars = terms("100.00", USD);

        enabled.add(USD);

        assertThatThrownBy(() -> createTransfer.create(dollars)).isInstanceOf(CurrencyNotEnabledException.class);
    }
}
