package com.carloscuatin.ledgerapp.ledger.domain;

/** No transfer with this identifier exists for the tenant asking, which is all a tenant can ever learn about another's. */
public class TransferNotFoundException extends DomainException {

    public TransferNotFoundException(TransferId id) {
        super("Transfer not found: " + id.value());
    }
}
