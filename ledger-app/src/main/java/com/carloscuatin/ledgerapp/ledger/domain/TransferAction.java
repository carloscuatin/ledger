package com.carloscuatin.ledgerapp.ledger.domain;

/**
 * What a caller asks a transfer to do, when the state of the transfer is only known at run time.
 */
public enum TransferAction {
    POST, VOID, EXPIRE
}
