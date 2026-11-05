package com.carloscuatin.ledgerapp.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.carloscuatin.ledgerapp.ledger.domain.AccountId;
import com.carloscuatin.ledgerapp.ledger.domain.DomainException;
import com.carloscuatin.ledgerapp.ledger.domain.Money;
import com.carloscuatin.ledgerapp.ledger.domain.TransferId;
import com.carloscuatin.ledgerapp.ledger.domain.TransferNotFoundException;
import com.carloscuatin.ledgerapp.ledger.domain.TransferStatus;
import com.carloscuatin.ledgerapp.ledger.domain.TransferTerms;

/** The port is a mock: these tests need no container, no database and no network. */
class FindTransferTest {

    private static final TransferId ID = new TransferId(UUID.fromString("00000000-0000-7000-8000-000000000001"));
    private static final TransferTerms TERMS = new TransferTerms(
            new AccountId(UUID.fromString("00000000-0000-7000-8000-000000000002")),
            new AccountId(UUID.fromString("00000000-0000-7000-8000-000000000003")),
            Money.parse("150000.00", Currency.getInstance("COP")));

    private final TransferQueries queries = mock();
    private final FindTransfer findTransfer = new FindTransfer(queries);

    /** A read reports the state a row is in, so it has no reason to tell a hold from an outcome. */
    @ParameterizedTest
    @EnumSource(TransferStatus.class)
    void anExistingTransferIsReturnedAsThePortAnswers(TransferStatus status) {
        var view = new TransferView(ID, TERMS, status);
        when(queries.find(ID)).thenReturn(Optional.of(view));

        assertThat(findTransfer.find(ID)).isSameAs(view);
    }

    @Test
    void anUnknownTransferRaisesADomainErrorThatNamesIt() {
        when(queries.find(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> findTransfer.find(ID))
                .isExactlyInstanceOf(TransferNotFoundException.class)
                .isInstanceOf(DomainException.class)
                .hasMessage("Transfer not found: " + ID.value());
    }

    @Test
    void aNullIdentifierIsRejectedBeforeThePort() {
        assertThatThrownBy(() -> findTransfer.find(null)).isInstanceOf(NullPointerException.class).hasMessage("id");
        verifyNoInteractions(queries);
    }

    @Test
    void aNullPortIsRejected() {
        assertThatThrownBy(() -> new FindTransfer(null)).isInstanceOf(NullPointerException.class).hasMessage("queries");
    }

    @Test
    void aViewNeedsEveryPart() {
        assertThatThrownBy(() -> new TransferView(null, TERMS, TransferStatus.PENDING))
                .isInstanceOf(NullPointerException.class).hasMessage("id");
        assertThatThrownBy(() -> new TransferView(ID, null, TransferStatus.PENDING))
                .isInstanceOf(NullPointerException.class).hasMessage("terms");
        assertThatThrownBy(() -> new TransferView(ID, TERMS, null))
                .isInstanceOf(NullPointerException.class).hasMessage("status");
    }
}
