package com.carloscuatin.ledgerapp.ledger.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

/**
 * What every other database test relies on. Row level security does not apply to a superuser or to a role with
 * BYPASSRLS, so an isolation test connected that way would pass while proving nothing.
 */
class DatabaseSetupIT {

    private static final String THIS_ROLE = """
            SELECT current_user AS name, rolsuper, rolbypassrls
            FROM pg_roles
            WHERE rolname = current_user
            """;

    @Test
    void theApplicationConnectsWithoutPrivilegesThatEscapeRowLevelSecurity() throws SQLException {
        try (var connection = LedgerDatabase.asApplication()) {
            assertThatTheRole(connection, "ledger_app", false);
        }
    }

    @Test
    void theMigratorConnectsWithoutPrivilegesThatEscapeRowLevelSecurity() throws SQLException {
        try (var connection = LedgerDatabase.asMigrator()) {
            assertThatTheRole(connection, "ledger_migrator", false);
        }
    }

    @Test
    void theControlConnectionIsASuperuser() throws SQLException {
        try (var connection = LedgerDatabase.asSuperuser();
                var statement = connection.createStatement();
                var rows = statement.executeQuery(THIS_ROLE)) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getBoolean("rolsuper")).isTrue();
        }
    }

    @Test
    void theServerIsTheOldestPostgresqlVersionTheLedgerSupports() throws SQLException {
        try (var connection = LedgerDatabase.asApplication();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT current_setting('server_version_num')::int")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt(1)).isBetween(160000, 169999);
        }
    }

    private static void assertThatTheRole(Connection connection, String name, boolean privileged) throws SQLException {
        try (var statement = connection.createStatement(); ResultSet rows = statement.executeQuery(THIS_ROLE)) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString("name")).isEqualTo(name);
            assertThat(rows.getBoolean("rolsuper")).isEqualTo(privileged);
            assertThat(rows.getBoolean("rolbypassrls")).isEqualTo(privileged);
        }
    }
}
