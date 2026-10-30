package com.carloscuatin.ledgerapp.ledger.domain;

/**
 * Which side of an account a posting moves. The side carries the direction of the movement, so posting amounts are
 * never negative.
 */
public enum Side {
    DEBIT, CREDIT
}
