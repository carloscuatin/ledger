package com.carloscuatin.ledgerapp.ledger.application;

import java.util.Objects;

import com.carloscuatin.ledgerapp.ledger.domain.TransferId;
import com.carloscuatin.ledgerapp.ledger.domain.TransferStatus;
import com.carloscuatin.ledgerapp.ledger.domain.TransferTerms;

/**
 * What a read shows of a transfer, in any of its states. It is a value and not the aggregate: a posted transfer cannot be
 * built without its journal entry, and showing one does not need that entry.
 */
public record TransferView(TransferId id, TransferTerms terms, TransferStatus status) {

    public TransferView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(terms, "terms");
        Objects.requireNonNull(status, "status");
    }
}
