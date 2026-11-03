package com.carloscuatin.ledgerapp.ledger.application;

import com.carloscuatin.ledgerapp.ledger.domain.PendingTransfer;

/**
 * Where transfers are kept. It takes part in the transaction of the caller and never opens one, so what a use case does
 * is stored whole or not at all.
 */
public interface Transfers {

    void add(PendingTransfer hold);
}
