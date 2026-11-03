package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

/**
 * A well formed code that names no currency with minor units, so no amount can be expressed in it. A domain error would
 * not fit: the domain works with currencies and never sees this code.
 */
final class UnusableCurrencyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    UnusableCurrencyException() {
        super("The currency does not exist or has no minor units");
    }
}
