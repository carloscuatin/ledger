package com.carloscuatin.ledgerapp.ledger.adapters.out.persistence;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.carloscuatin.ledgerapp.ledger.application.Transfers;
import com.carloscuatin.ledgerapp.ledger.domain.PendingTransfer;

import jakarta.persistence.EntityManager;

/**
 * Stores transfers through JPA. The identifier comes from the domain, so persist sends a plain INSERT, where merge would
 * first have to look the row up.
 */
class JpaTransfers implements Transfers {

    private final EntityManager entityManager;

    JpaTransfers(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** Demands a transaction and never opens one, so a use case that forgot to declare one fails instead of saving. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void add(PendingTransfer hold) {
        entityManager.persist(new TransferEntity(hold));
    }
}
