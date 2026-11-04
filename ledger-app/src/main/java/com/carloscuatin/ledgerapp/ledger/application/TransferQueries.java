package com.carloscuatin.ledgerapp.ledger.application;

import java.util.Optional;

import com.carloscuatin.ledgerapp.ledger.domain.TransferId;

/**
 * What can be read about transfers, apart from {@link Transfers}: answering a question needs values, not aggregates to
 * change. It takes part in the transaction of the caller and never opens one, because the tenant of a read is set when a
 * transaction begins.
 */
public interface TransferQueries {

    /** Empty both when no such transfer exists and when it belongs to another tenant: row level security decides. */
    Optional<TransferView> find(TransferId id);
}
