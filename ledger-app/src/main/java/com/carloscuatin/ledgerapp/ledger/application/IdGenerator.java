package com.carloscuatin.ledgerapp.ledger.application;

import java.util.UUID;

/**
 * Gives out identifiers for new entities. It is a port so that use cases never read a clock or a source of randomness,
 * and the format of an identifier is decided by an adapter.
 */
public interface IdGenerator {

    UUID next();
}
