package com.carloscuatin.ledgerapp.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class TransferTest {

    private static final Currency COP = Currency.getInstance("COP");
    private static final AccountId SOURCE = new AccountId(UUID.fromString("00000000-0000-7000-8000-000000000001"));
    private static final AccountId DESTINATION = new AccountId(UUID.fromString("00000000-0000-7000-8000-000000000002"));
    private static final Money AMOUNT = Money.parse("150000.00", COP);
    private static final TransferTerms TERMS = new TransferTerms(SOURCE, DESTINATION, AMOUNT);
    private static final JournalEntry ENTRY = new JournalEntry(List.of(
            new Posting(SOURCE, Side.DEBIT, AMOUNT), new Posting(DESTINATION, Side.CREDIT, AMOUNT)));

    @Test
    void transferIsSealedToExactlyTheFourStates() {
        assertThat(Transfer.class.isSealed()).isTrue();
        assertThat(Transfer.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(
                        PendingTransfer.class, PostedTransfer.class, VoidedTransfer.class, ExpiredTransfer.class);
    }

    @Test
    void everyStateKeepsItsTerms() {
        List<Transfer> states = List.of(
                new PendingTransfer(TERMS),
                new PostedTransfer(TERMS, ENTRY),
                new VoidedTransfer(TERMS),
                new ExpiredTransfer(TERMS));

        assertThat(states).extracting(Transfer::terms).containsOnly(TERMS);
    }

    @Test
    void aPostedTransferKeepsItsJournalEntry() {
        var posted = new PostedTransfer(TERMS, ENTRY);

        assertThat(posted.entry()).isEqualTo(ENTRY);
    }

    @Test
    void nullStatePartsAreRejected() {
        assertThatThrownBy(() -> new PendingTransfer(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PostedTransfer(null, ENTRY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PostedTransfer(TERMS, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new VoidedTransfer(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ExpiredTransfer(null)).isInstanceOf(NullPointerException.class);
    }
}
