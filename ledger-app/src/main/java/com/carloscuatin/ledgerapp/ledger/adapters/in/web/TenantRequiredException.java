package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

/** The request is authenticated and has no tenant to act for, so no tenant scope can be opened for it. */
final class TenantRequiredException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    TenantRequiredException() {
        super("The authenticated principal has no tenant");
    }
}
