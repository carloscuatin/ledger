package com.carloscuatin.ledgerapp.ledger.adapters.out.persistence;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Plain JDBC on the transfer table for tests that must not depend on Spring or JPA: if they fail, the problem is in
 * the database. Each test uses new tenants, so rows left by other tests never get in the way.
 */
public final class TransferRows {

    private TransferRows() {
    }

    /** Sets the tenant for the current transaction only, the way the application does it. */
    static void setTenant(Connection connection, UUID tenant) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
            statement.setString(1, tenant.toString());
            statement.execute();
        }
    }

    /** Commits one row for a tenant as the application role, and returns its id. */
    public static UUID seed(UUID tenant) throws SQLException {
        try (var connection = LedgerDatabase.asApplication()) {
            connection.setAutoCommit(false);
            setTenant(connection, tenant);
            var id = UUID.randomUUID();
            insert(connection, id);
            connection.commit();
            return id;
        }
    }

    /** Inserts a valid row without naming its tenant. */
    static void insert(Connection connection, UUID id) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO transfer (id, source_account_id, destination_account_id, amount_minor, currency, status)
                VALUES (?, ?, ?, 1, 'COP', 'PENDING')
                """)) {
            statement.setObject(1, id);
            statement.setObject(2, UUID.randomUUID());
            statement.setObject(3, UUID.randomUUID());
            statement.executeUpdate();
        }
    }

    /** Inserts a valid row that names its tenant. */
    static void insertFor(Connection connection, UUID id, UUID tenant) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO transfer
                    (id, tenant_id, source_account_id, destination_account_id, amount_minor, currency, status)
                VALUES (?, ?, ?, ?, 1, 'COP', 'PENDING')
                """)) {
            statement.setObject(1, id);
            statement.setObject(2, tenant);
            statement.setObject(3, UUID.randomUUID());
            statement.setObject(4, UUID.randomUUID());
            statement.executeUpdate();
        }
    }

    /** Every row this connection can see, with no filter of its own. */
    static List<UUID> visibleIds(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT id FROM transfer")) {
            return ids(rows);
        }
    }

    /** What really is in the table for these identifiers, seen past row level security. */
    static List<UUID> idsInTheTable(UUID... ids) throws SQLException {
        try (var connection = LedgerDatabase.asSuperuser();
                var statement = connection.prepareStatement("SELECT id FROM transfer WHERE id = ANY (?)")) {
            statement.setArray(1, connection.createArrayOf("uuid", ids));
            try (var rows = statement.executeQuery()) {
                return ids(rows);
            }
        }
    }

    /** How many rows the table really holds, seen past row level security. */
    public static long countInTheTable() throws SQLException {
        try (var connection = LedgerDatabase.asSuperuser();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT count(*) FROM transfer")) {
            rows.next();
            return rows.getLong(1);
        }
    }

    /** How many rows a tenant really holds, seen past row level security. */
    public static long countInTheTableFor(UUID tenant) throws SQLException {
        try (var connection = LedgerDatabase.asSuperuser();
                var statement = connection.prepareStatement("SELECT count(*) FROM transfer WHERE tenant_id = ?")) {
            statement.setObject(1, tenant);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    /** The tenant a row really belongs to, seen past row level security. */
    public static UUID tenantOf(UUID id) throws SQLException {
        return column("tenant_id", id, UUID.class);
    }

    /** The amount a row really holds, seen past row level security. */
    public static long amountMinorOf(UUID id) throws SQLException {
        return column("amount_minor", id, Long.class);
    }

    private static <T> T column(String name, UUID id, Class<T> type) throws SQLException {
        try (var connection = LedgerDatabase.asSuperuser();
                var statement = connection.prepareStatement("SELECT " + name + " FROM transfer WHERE id = ?")) {
            statement.setObject(1, id);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new AssertionError("No transfer " + id);
                }
                return rows.getObject(1, type);
            }
        }
    }

    static String currentUser(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT current_user")) {
            rows.next();
            return rows.getString(1);
        }
    }

    private static List<UUID> ids(ResultSet rows) throws SQLException {
        var ids = new ArrayList<UUID>();
        while (rows.next()) {
            ids.add(rows.getObject(1, UUID.class));
        }
        return ids;
    }
}
