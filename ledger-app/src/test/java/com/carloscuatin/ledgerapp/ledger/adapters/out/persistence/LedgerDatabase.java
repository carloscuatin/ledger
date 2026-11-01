package com.carloscuatin.ledgerapp.ledger.adapters.out.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One real PostgreSQL per test run, shared by every database test. The container starts when this class is first used
 * and Testcontainers removes it when the JVM exits; tests stay isolated by using tenants of their own, not by having
 * their own server. The image is the oldest version the ledger supports, so nothing newer can be relied on by accident.
 */
final class LedgerDatabase {

    private static final PostgreSQLContainer SERVER = new PostgreSQLContainer("postgres:16.15-alpine")
            .withInitScript("db/roles.sql");

    static {
        SERVER.start();
    }

    private LedgerDatabase() {
    }

    static Connection asApplication() throws SQLException {
        return DriverManager.getConnection(SERVER.getJdbcUrl(), "ledger_app", "test-only-app");
    }

    /** The role that owns the tables and runs the migrations. */
    static Connection asMigrator() throws SQLException {
        return DriverManager.getConnection(SERVER.getJdbcUrl(), "ledger_migrator", "test-only-migrator");
    }

    /** Sees past row level security, so it can only ever be a control that checks what really is in a table. */
    static Connection asSuperuser() throws SQLException {
        return DriverManager.getConnection(SERVER.getJdbcUrl(), SERVER.getUsername(), SERVER.getPassword());
    }
}
