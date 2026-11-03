package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.context.annotation.Lazy;

/**
 * A bean created at startup whatever the setting, that needs the migration to have run. Sources are registered before
 * anything is scanned, so given to a process as its first source it is the first bean created, and migrating is the first
 * thing the process does unless something stops it earlier. It carries no stereotype, so a scan never finds it.
 */
@Lazy(false)
class DemandsTheMigration {

    DemandsTheMigration(FlywayMigrationInitializer migration) {
    }
}
