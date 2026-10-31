package com.carloscuatin.ledgerapp.ledger.domain;

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
}
