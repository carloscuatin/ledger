package com.carloscuatin.ledgerapp.ledger.domain;

/**
 * Money promised from one account to another, in one of a closed set of states. Each state is its own type, so a
 * state that cannot exist, such as a posted transfer without its journal entry, cannot be built either.
 */
public sealed interface Transfer permits PendingTransfer, PostedTransfer, VoidedTransfer, ExpiredTransfer {

    TransferTerms terms();
}
