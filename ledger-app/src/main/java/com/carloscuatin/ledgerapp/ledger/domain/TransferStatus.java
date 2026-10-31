package com.carloscuatin.ledgerapp.ledger.domain;

/**
 * The state of a transfer as a value, for messages, storage and events; it always follows from the type of the state.
 */
public enum TransferStatus {
    PENDING, POSTED, VOIDED, EXPIRED
}
