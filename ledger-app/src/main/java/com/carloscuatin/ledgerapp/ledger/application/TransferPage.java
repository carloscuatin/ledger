package com.carloscuatin.ledgerapp.ledger.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.carloscuatin.ledgerapp.ledger.domain.TransferId;

/**
 * Part of the transfers of a tenant, newest first. {@code next} is the position to continue from, empty when no rows
 * remain, so a caller never has to read an empty page to find the end.
 */
public record TransferPage(List<TransferView> items, Optional<TransferId> next) {

    public TransferPage {
        items = List.copyOf(items);
        Objects.requireNonNull(next, "next");
    }
}
