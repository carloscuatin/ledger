package com.carloscuatin.ledgerapp.ledger.application;

import java.util.Objects;

import org.springframework.transaction.annotation.Transactional;

import com.carloscuatin.ledgerapp.ledger.domain.TransferId;
import com.carloscuatin.ledgerapp.ledger.domain.TransferNotFoundException;

/**
 * Reads one transfer of the tenant in scope. The class is not final because the transaction boundary is applied by a
 * generated subclass. It holds only the port of queries, so nothing it calls can write in its read only transaction.
 */
public class FindTransfer {

    private final TransferQueries queries;

    public FindTransfer(TransferQueries queries) {
        this.queries = Objects.requireNonNull(queries, "queries");
    }

    @Transactional(readOnly = true)
    public TransferView find(TransferId id) {
        Objects.requireNonNull(id, "id");
        return queries.find(id).orElseThrow(() -> new TransferNotFoundException(id));
    }
}
