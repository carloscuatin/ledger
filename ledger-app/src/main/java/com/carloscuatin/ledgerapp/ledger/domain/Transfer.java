package com.carloscuatin.ledgerapp.ledger.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Money promised from one account to another, in one of a closed set of states. Each state is its own type, so a
 * state that cannot exist, such as a posted transfer without its journal entry, cannot be built either.
 */
public sealed interface Transfer permits PendingTransfer, PostedTransfer, VoidedTransfer, ExpiredTransfer {

    TransferTerms terms();

    /** The status follows from the type, so the two can never disagree. */
    default TransferStatus status() {
        // No default branch: the interface is sealed, so a new state fails to compile here until it is handled.
        return switch (this) {
            case PendingTransfer _ -> TransferStatus.PENDING;
            case PostedTransfer _ -> TransferStatus.POSTED;
            case VoidedTransfer _ -> TransferStatus.VOIDED;
            case ExpiredTransfer _ -> TransferStatus.EXPIRED;
        };
    }

    /** The journal entry that moved the money, present only once the transfer is posted. */
    Optional<JournalEntry> journalEntry();

    /**
     * Applies an action to a transfer whose state is only known at run time, such as one loaded from storage. Only a
     * hold can move; any other state rejects every action.
     */
    default Transfer apply(TransferAction action) {
        // Checked first: otherwise only a hold would reject a null action, since only a hold reaches the switch.
        Objects.requireNonNull(action, "action");
        if (this instanceof PendingTransfer pending) {
            return switch (action) {
                case POST -> pending.post();
                case VOID -> pending.voidHold();
                case EXPIRE -> pending.expire();
            };
        }
        throw new InvalidTransitionException(action, status());
    }
}
