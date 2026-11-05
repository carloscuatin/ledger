package com.carloscuatin.ledgerapp.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.carloscuatin.ledgerapp.ledger.domain.AccountId;
import com.carloscuatin.ledgerapp.ledger.domain.Money;
import com.carloscuatin.ledgerapp.ledger.domain.TransferId;
import com.carloscuatin.ledgerapp.ledger.domain.TransferStatus;
import com.carloscuatin.ledgerapp.ledger.domain.TransferTerms;

/** The port is a mock: these tests need no container, no database and no network. */
class ListTransfersTest {

    private static final TransferTerms TERMS = new TransferTerms(
            new AccountId(UUID.fromString("00000000-0000-7000-8000-000000000002")),
            new AccountId(UUID.fromString("00000000-0000-7000-8000-000000000003")),
            Money.parse("150000.00", Currency.getInstance("COP")));
    private static final TransferView NEWEST = view("00000000-0000-7000-8000-00000000000c");
    private static final TransferView MIDDLE = view("00000000-0000-7000-8000-00000000000b");
    private static final TransferView OLDEST = view("00000000-0000-7000-8000-00000000000a");

    private final TransferQueries queries = mock();
    private final ListTransfers listTransfers = new ListTransfers(queries);

    private static TransferView view(String id) {
        return new TransferView(new TransferId(UUID.fromString(id)), TERMS, TransferStatus.PENDING);
    }

    /** The row past the size is only a question to the database: it says there is more, and no client ever sees it. */
    @Test
    void aPageWithMoreRowsGivesTheLastOneItShowsAsTheNextPosition() {
        when(queries.newestFirst(Optional.empty(), 3)).thenReturn(List.of(NEWEST, MIDDLE, OLDEST));

        var page = listTransfers.list(Optional.empty(), 2);

        assertThat(page.items()).containsExactly(NEWEST, MIDDLE);
        assertThat(page.next()).contains(MIDDLE.id());
    }

    /** A full page that ends the rows is the case a client would otherwise follow into an empty page. */
    @Test
    void aFullPageThatEndsTheRowsHasNoNextPosition() {
        when(queries.newestFirst(Optional.empty(), 3)).thenReturn(List.of(NEWEST, MIDDLE));

        var page = listTransfers.list(Optional.empty(), 2);

        assertThat(page.items()).containsExactly(NEWEST, MIDDLE);
        assertThat(page.next()).isEmpty();
    }

    @Test
    void aPageWithFewerRowsHasNoNextPosition() {
        when(queries.newestFirst(Optional.empty(), 3)).thenReturn(List.of(NEWEST));

        var page = listTransfers.list(Optional.empty(), 2);

        assertThat(page.items()).containsExactly(NEWEST);
        assertThat(page.next()).isEmpty();
    }

    @Test
    void anEmptyPageHasNoNextPosition() {
        when(queries.newestFirst(Optional.empty(), 3)).thenReturn(List.of());

        var page = listTransfers.list(Optional.empty(), 2);

        assertThat(page.items()).isEmpty();
        assertThat(page.next()).isEmpty();
    }

    @Test
    void aPageOfOneStillLooksOneRowAhead() {
        when(queries.newestFirst(Optional.empty(), 2)).thenReturn(List.of(NEWEST, MIDDLE));

        var page = listTransfers.list(Optional.empty(), 1);

        assertThat(page.items()).containsExactly(NEWEST);
        assertThat(page.next()).contains(NEWEST.id());
    }

    @Test
    void thePositionReceivedIsHandedToThePort() {
        var position = Optional.of(NEWEST.id());
        when(queries.newestFirst(position, 3)).thenReturn(List.of(MIDDLE, OLDEST));

        var page = listTransfers.list(position, 2);

        verify(queries).newestFirst(position, 3);
        assertThat(page.items()).containsExactly(MIDDLE, OLDEST);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void aPageSizeBelowOneIsRejectedBeforeThePort(int limit) {
        assertThatThrownBy(() -> listTransfers.list(Optional.empty(), limit))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("A page holds at least one transfer");
        verifyNoInteractions(queries);
    }

    @Test
    void aNullPositionIsRejectedBeforeThePort() {
        assertThatThrownBy(() -> listTransfers.list(null, 2)).isInstanceOf(NullPointerException.class)
                .hasMessage("before");
        verifyNoInteractions(queries);
    }

    @Test
    void aNullPortIsRejected() {
        assertThatThrownBy(() -> new ListTransfers(null)).isInstanceOf(NullPointerException.class)
                .hasMessage("queries");
    }

    @Test
    void aPageNeedsItsParts() {
        var items = List.of(NEWEST);
        var none = Optional.<TransferId>empty();

        assertThatThrownBy(() -> new TransferPage(null, none)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TransferPage(items, null)).isInstanceOf(NullPointerException.class)
                .hasMessage("next");
    }

    /** What the port handed over may be a view of a longer list, which a page has no reason to keep alive or share. */
    @Test
    void aPageOwnsItsItems() {
        var page = new TransferPage(new ArrayList<>(List.of(NEWEST)), Optional.empty());

        assertThatThrownBy(() -> page.items().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
