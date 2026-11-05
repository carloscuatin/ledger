package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

/**
 * A cursor that does not decode to a position. A domain error would not fit: the domain deals in positions and never
 * sees the form a client carries them in. The message is constant because the cursor is whatever the client sent.
 */
final class InvalidCursorException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    InvalidCursorException() {
        super("The cursor does not decode to a position");
    }
}
