package com.carloscuatin.ledgerapp.ledger.adapters.out.persistence;

import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.carloscuatin.ledgerapp.ledger.domain.PendingTransfer;
import com.carloscuatin.ledgerapp.ledger.domain.TransferStatus;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The row of a transfer, kept apart from the domain so that neither knows how the other changes. The tenant is
 * deliberately not mapped: the database takes it from the transaction, so no code here can get it wrong.
 */
// Hibernate reads the fields directly, and nothing else needs to.
@SuppressWarnings("UnusedVariable")
@Entity
@Table(name = "transfer")
class TransferEntity {

    @Id
    private UUID id;

    private UUID sourceAccountId;

    private UUID destinationAccountId;

    private long amountMinor;

    // The column is a fixed char, and schema validation at startup compares the type, not only the name.
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency;

    @Enumerated(EnumType.STRING)
    private TransferStatus status;

    /** JPA creates instances through a constructor without arguments. */
    protected TransferEntity() {
    }

    TransferEntity(PendingTransfer hold) {
        this.id = hold.id().value();
        this.sourceAccountId = hold.terms().source().value();
        this.destinationAccountId = hold.terms().destination().value();
        this.amountMinor = hold.terms().amount().minorUnits();
        this.currency = hold.terms().amount().currency().getCurrencyCode();
        this.status = hold.status();
    }
}
