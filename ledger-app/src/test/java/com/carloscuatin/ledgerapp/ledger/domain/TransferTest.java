package com.carloscuatin.ledgerapp.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

    @Test
    void postingAHoldDebitsTheSourceAndCreditsTheDestination() {
        var posted = new PendingTransfer(TERMS).post();

        assertThat(posted.terms()).isEqualTo(TERMS);
        assertThat(posted.entry().postings())
                .containsExactly(new Posting(SOURCE, Side.DEBIT, AMOUNT),
                        new Posting(DESTINATION, Side.CREDIT, AMOUNT));
    }

    @Test
    void postingKeepsTheCurrencyOfTheHold() {
        var yen = Money.parse("100", Currency.getInstance("JPY"));
        var posted = new PendingTransfer(new TransferTerms(SOURCE, DESTINATION, yen)).post();

        assertThat(posted.entry().postings())
                .containsExactly(new Posting(SOURCE, Side.DEBIT, yen), new Posting(DESTINATION, Side.CREDIT, yen));
    }

    @Test
    void voidingAHoldKeepsItsTerms() {
        VoidedTransfer voided = new PendingTransfer(TERMS).voidHold();

        assertThat(voided.terms()).isEqualTo(TERMS);
    }

    @Test
    void expiringAHoldKeepsItsTerms() {
        ExpiredTransfer expired = new PendingTransfer(TERMS).expire();

        assertThat(expired.terms()).isEqualTo(TERMS);
    }

    @Test
    void transitionsReturnANewStateAndLeaveTheHoldUnchanged() {
        var pending = new PendingTransfer(TERMS);

        pending.post();

        assertThat(pending).isEqualTo(new PendingTransfer(TERMS));
    }

    @Test
    void onlyAHoldHasTransitions() {
        var transitions = List.of("post", "voidHold", "expire");

        assertThat(publicMethodNames(PendingTransfer.class)).containsAll(transitions);
        for (var terminal : List.of(PostedTransfer.class, VoidedTransfer.class, ExpiredTransfer.class)) {
            assertThat(publicMethodNames(terminal)).as(terminal.getSimpleName())
                    .contains("terms", "journalEntry")
                    .doesNotContainAnyElementsOf(transitions);
        }
    }

    @ParameterizedTest
    @MethodSource("statesAndTheirStatus")
    void everyStateReportsItsStatus(Transfer transfer, TransferStatus expected) {
        assertThat(transfer.status()).isEqualTo(expected);
    }

    static Stream<Arguments> statesAndTheirStatus() {
        return Stream.of(
                Arguments.of(new PendingTransfer(TERMS), TransferStatus.PENDING),
                Arguments.of(new PostedTransfer(TERMS, ENTRY), TransferStatus.POSTED),
                Arguments.of(new VoidedTransfer(TERMS), TransferStatus.VOIDED),
                Arguments.of(new ExpiredTransfer(TERMS), TransferStatus.EXPIRED));
    }

    @Test
    void aPostedTransferExposesItsJournalEntry() {
        Transfer transfer = new PendingTransfer(TERMS).post();

        if (!(transfer instanceof PostedTransfer(TransferTerms(var source, var destination, var amount), var entry))) {
            fail("posting a hold must produce a posted transfer, got " + transfer);
            return;
        }
        assertThat(source).isEqualTo(SOURCE);
        assertThat(destination).isEqualTo(DESTINATION);
        assertThat(amount).isEqualTo(AMOUNT);
        assertThat(transfer.journalEntry()).contains(entry);
    }

    @Test
    void onlyAPostedTransferHasAJournalEntry() {
        List<Transfer> withoutEntry = List.of(new PendingTransfer(TERMS), new VoidedTransfer(TERMS),
                new ExpiredTransfer(TERMS));

        assertThat(withoutEntry).hasSize(3).allSatisfy(transfer -> assertThat(transfer.journalEntry()).isEmpty());
    }

    private static List<String> publicMethodNames(Class<?> type) {
        return Arrays.stream(type.getMethods()).map(Method::getName).toList();
    }
}
