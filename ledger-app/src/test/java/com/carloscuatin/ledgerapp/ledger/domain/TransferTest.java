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
    private static final TransferId ID = new TransferId(UUID.fromString("00000000-0000-7000-8000-000000000003"));
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
    void everyStateKeepsItsIdentityAndTerms() {
        List<Transfer> states = List.of(
                new PendingTransfer(ID, TERMS),
                new PostedTransfer(ID, TERMS, ENTRY),
                new VoidedTransfer(ID, TERMS),
                new ExpiredTransfer(ID, TERMS));

        assertThat(states).extracting(Transfer::id).hasSize(4).containsOnly(ID);
        assertThat(states).extracting(Transfer::terms).containsOnly(TERMS);
    }

    @Test
    void aPostedTransferKeepsItsJournalEntry() {
        var posted = new PostedTransfer(ID, TERMS, ENTRY);

        assertThat(posted.entry()).isEqualTo(ENTRY);
    }

    @Test
    void nullStatePartsAreRejected() {
        assertThatThrownBy(() -> new PendingTransfer(null, TERMS)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PendingTransfer(ID, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PostedTransfer(null, TERMS, ENTRY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PostedTransfer(ID, null, ENTRY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PostedTransfer(ID, TERMS, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new VoidedTransfer(null, TERMS)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new VoidedTransfer(ID, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ExpiredTransfer(null, TERMS)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ExpiredTransfer(ID, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void postingAHoldDebitsTheSourceAndCreditsTheDestination() {
        var posted = new PendingTransfer(ID, TERMS).post();

        assertThat(posted.id()).isEqualTo(ID);
        assertThat(posted.terms()).isEqualTo(TERMS);
        assertThat(posted.entry().postings())
                .containsExactly(new Posting(SOURCE, Side.DEBIT, AMOUNT),
                        new Posting(DESTINATION, Side.CREDIT, AMOUNT));
    }

    @Test
    void postingKeepsTheCurrencyOfTheHold() {
        var yen = Money.parse("100", Currency.getInstance("JPY"));
        var posted = new PendingTransfer(ID, new TransferTerms(SOURCE, DESTINATION, yen)).post();

        assertThat(posted.entry().postings())
                .containsExactly(new Posting(SOURCE, Side.DEBIT, yen), new Posting(DESTINATION, Side.CREDIT, yen));
    }

    @Test
    void voidingAHoldKeepsItsIdentityAndTerms() {
        VoidedTransfer voided = new PendingTransfer(ID, TERMS).voidHold();

        assertThat(voided.id()).isEqualTo(ID);
        assertThat(voided.terms()).isEqualTo(TERMS);
    }

    @Test
    void expiringAHoldKeepsItsIdentityAndTerms() {
        ExpiredTransfer expired = new PendingTransfer(ID, TERMS).expire();

        assertThat(expired.id()).isEqualTo(ID);
        assertThat(expired.terms()).isEqualTo(TERMS);
    }

    @Test
    void transitionsReturnANewStateAndLeaveTheHoldUnchanged() {
        var pending = new PendingTransfer(ID, TERMS);

        pending.post();

        assertThat(pending).isEqualTo(new PendingTransfer(ID, TERMS));
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
                Arguments.of(new PendingTransfer(ID, TERMS), TransferStatus.PENDING),
                Arguments.of(new PostedTransfer(ID, TERMS, ENTRY), TransferStatus.POSTED),
                Arguments.of(new VoidedTransfer(ID, TERMS), TransferStatus.VOIDED),
                Arguments.of(new ExpiredTransfer(ID, TERMS), TransferStatus.EXPIRED));
    }

    @Test
    void aPostedTransferExposesItsJournalEntry() {
        Transfer transfer = new PendingTransfer(ID, TERMS).post();

        if (!(transfer instanceof PostedTransfer(var id, TransferTerms(var source, var destination, var amount), var entry))) {
            fail("posting a hold must produce a posted transfer, got " + transfer);
            return;
        }
        assertThat(id).isEqualTo(ID);
        assertThat(source).isEqualTo(SOURCE);
        assertThat(destination).isEqualTo(DESTINATION);
        assertThat(amount).isEqualTo(AMOUNT);
        assertThat(transfer.journalEntry()).contains(entry);
    }

    @Test
    void onlyAPostedTransferHasAJournalEntry() {
        List<Transfer> withoutEntry = List.of(new PendingTransfer(ID, TERMS), new VoidedTransfer(ID, TERMS),
                new ExpiredTransfer(ID, TERMS));

        assertThat(withoutEntry).hasSize(3).allSatisfy(transfer -> assertThat(transfer.journalEntry()).isEmpty());
    }

    @Test
    void applyingAnActionToAHoldReachesItsState() {
        Transfer pending = new PendingTransfer(ID, TERMS);

        assertThat(pending.apply(TransferAction.POST)).isEqualTo(new PendingTransfer(ID, TERMS).post());
        assertThat(pending.apply(TransferAction.VOID)).isEqualTo(new VoidedTransfer(ID, TERMS));
        assertThat(pending.apply(TransferAction.EXPIRE)).isEqualTo(new ExpiredTransfer(ID, TERMS));
    }

    @ParameterizedTest
    @MethodSource("finalStatesAndActions")
    void aFinalStateRejectsEveryAction(Transfer transfer, TransferAction action, String expectedMessage) {
        assertThatThrownBy(() -> transfer.apply(action))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessage(expectedMessage);
    }

    static Stream<Arguments> finalStatesAndActions() {
        return Stream.of(
                Arguments.of(new PostedTransfer(ID, TERMS, ENTRY), TransferAction.POST,
                        "Cannot POST a transfer that is POSTED"),
                Arguments.of(new PostedTransfer(ID, TERMS, ENTRY), TransferAction.VOID,
                        "Cannot VOID a transfer that is POSTED"),
                Arguments.of(
                        new PostedTransfer(ID, TERMS, ENTRY), TransferAction.EXPIRE,
                        "Cannot EXPIRE a transfer that is POSTED"),
                Arguments.of(new VoidedTransfer(ID, TERMS), TransferAction.POST,
                        "Cannot POST a transfer that is VOIDED"),
                Arguments.of(new VoidedTransfer(ID, TERMS), TransferAction.VOID,
                        "Cannot VOID a transfer that is VOIDED"),
                Arguments.of(new VoidedTransfer(ID, TERMS), TransferAction.EXPIRE,
                        "Cannot EXPIRE a transfer that is VOIDED"),
                Arguments.of(new ExpiredTransfer(ID, TERMS), TransferAction.POST,
                        "Cannot POST a transfer that is EXPIRED"),
                Arguments.of(new ExpiredTransfer(ID, TERMS), TransferAction.VOID,
                        "Cannot VOID a transfer that is EXPIRED"),
                Arguments.of(
                        new ExpiredTransfer(ID, TERMS), TransferAction.EXPIRE,
                        "Cannot EXPIRE a transfer that is EXPIRED"));
    }

    @Test
    void aNullActionIsRejectedInEveryState() {
        List<Transfer> states = List.of(
                new PendingTransfer(ID, TERMS),
                new PostedTransfer(ID, TERMS, ENTRY),
                new VoidedTransfer(ID, TERMS),
                new ExpiredTransfer(ID, TERMS));

        assertThat(states).hasSize(4).allSatisfy(transfer -> assertThatThrownBy(() -> transfer.apply(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("action"));
    }

    @Test
    void theNewErrorsShareTheDomainBaseType() {
        var sameAccount = new SameAccountTransferException(SOURCE);
        var invalidTransition = new InvalidTransitionException(TransferAction.POST, TransferStatus.POSTED);

        assertThat(sameAccount).isInstanceOf(DomainException.class).isInstanceOf(RuntimeException.class);
        assertThat(invalidTransition).isInstanceOf(DomainException.class).isInstanceOf(RuntimeException.class);
    }

    private static List<String> publicMethodNames(Class<?> type) {
        return Arrays.stream(type.getMethods()).map(Method::getName).toList();
    }
}
