package com.carloscuatin.ledgerapp.ledger.adapters.out.persistence;

import static com.carloscuatin.ledgerapp.ledger.adapters.out.persistence.TransferRows.currentUser;
import static com.carloscuatin.ledgerapp.ledger.adapters.out.persistence.TransferRows.setTenant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The application role holds only what it needs, so even a statement that should never have been written cannot lift
 * the protection or rewrite a transfer.
 */
class ApplicationRoleIT {

    private static final String INSUFFICIENT_PRIVILEGE = "42501";

    private static Connection connectAsTheApplication() throws SQLException {
        var connection = LedgerDatabase.asApplication();
        assertThat(currentUser(connection)).isEqualTo("ledger_app");
        return connection;
    }

    private static void assertRefused(Connection connection, String sql, String message) {
        assertThatThrownBy(() -> {
            try (var statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }).isInstanceOfSatisfying(SQLException.class, failure -> assertThat(failure.getSQLState())
                .isEqualTo(INSUFFICIENT_PRIVILEGE))
                .hasMessageContaining(message);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "ALTER TABLE transfer DISABLE ROW LEVEL SECURITY",
        "ALTER TABLE transfer NO FORCE ROW LEVEL SECURITY",
        "DROP POLICY tenant_isolation ON transfer",
        "DROP TABLE transfer"
    })
    void theProtectionCannotBeChanged(String sql) throws SQLException {
        try (var connection = connectAsTheApplication()) {
            assertRefused(connection, sql, "must be owner");
        }

        try (var connection = LedgerDatabase.asSuperuser();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("""
                        SELECT c.relrowsecurity, c.relforcerowsecurity, count(p.polname) AS policies
                        FROM pg_class c
                        LEFT JOIN pg_policy p ON p.polrelid = c.oid AND p.polname = 'tenant_isolation'
                        WHERE c.oid = 'public.transfer'::regclass
                        GROUP BY c.relrowsecurity, c.relforcerowsecurity
                        """)) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getBoolean("relrowsecurity")).isTrue();
            assertThat(rows.getBoolean("relforcerowsecurity")).isTrue();
            assertThat(rows.getInt("policies")).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "UPDATE transfer SET amount_minor = 2 WHERE id = '%s'",
        "DELETE FROM transfer WHERE id = '%s'"
    })
    void aTransferCannotBeUpdatedOrDeleted(String sql) throws SQLException {
        var tenant = UUID.randomUUID();
        var id = TransferRows.seed(tenant);

        try (var connection = connectAsTheApplication()) {
            connection.setAutoCommit(false);
            setTenant(connection, tenant);
            assertRefused(connection, sql.formatted(id), "permission denied for table transfer");
        }

        try (var connection = LedgerDatabase.asSuperuser();
                var statement = connection.prepareStatement("SELECT amount_minor FROM transfer WHERE id = ?")) {
            statement.setObject(1, id);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isEqualTo(1);
            }
        }
    }

    @Test
    void theMigrationHistoryCannotBeRead() throws SQLException {
        try (var connection = connectAsTheApplication()) {
            assertRefused(connection, "SELECT * FROM flyway_schema_history", "permission denied");
        }
    }
}
