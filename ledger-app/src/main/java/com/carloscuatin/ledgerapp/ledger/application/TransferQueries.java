package com.carloscuatin.ledgerapp.ledger.application;

import java.util.List;
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

    /**
     * Up to {@code rows} transfers of the tenant in scope, in descending order of identifier, starting right after
     * {@code before} or from the newest when it is empty. The order is the one the store gives identifiers, which a caller
     * has no way to reproduce with {@link java.util.UUID#compareTo}: that one compares with a sign.
     */
    List<TransferView> newestFirst(Optional<TransferId> before, int rows);
}
