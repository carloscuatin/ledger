package com.carloscuatin.ledgerapp.ledger.application;

import java.util.Objects;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import com.carloscuatin.ledgerapp.ledger.domain.TransferId;

/**
 * Reads one page of the transfers of the tenant in scope. The class is not final because the transaction boundary is
 * applied by a generated subclass. It holds only the port of queries, so nothing it calls can write in its read only
 * transaction.
 */
public class ListTransfers {

    private final TransferQueries queries;

    public ListTransfers(TransferQueries queries) {
        this.queries = Objects.requireNonNull(queries, "queries");
    }

    /**
     * Asks for one row more than the page holds, because a full page alone cannot tell whether it was the last one. That
     * extra row is never shown: the next position is the last row shown, so the next page starts with the row held back.
     */
    @Transactional(readOnly = true)
    public TransferPage list(Optional<TransferId> before, int limit) {
        Objects.requireNonNull(before, "before");
        if (limit < 1) {
            throw new IllegalArgumentException("A page holds at least one transfer");
        }
        var rows = queries.newestFirst(before, limit + 1);
        if (rows.size() > limit) {
            var shown = rows.subList(0, limit);
            return new TransferPage(shown, Optional.of(shown.getLast().id()));
        }
        return new TransferPage(rows, Optional.empty());
    }
}
