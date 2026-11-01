package com.carloscuatin.ledgerapp.ledger.adapters.out.persistence;

import static com.carloscuatin.ledgerapp.ledger.adapters.out.persistence.TransferRows.currentUser;
import static com.carloscuatin.ledgerapp.ledger.adapters.out.persistence.TransferRows.idsInTheTable;
import static com.carloscuatin.ledgerapp.ledger.adapters.out.persistence.TransferRows.insert;
import static com.carloscuatin.ledgerapp.ledger.adapters.out.persistence.TransferRows.insertFor;
import static com.carloscuatin.ledgerapp.ledger.adapters.out.persistence.TransferRows.seed;
import static com.carloscuatin.ledgerapp.ledger.adapters.out.persistence.TransferRows.setTenant;
import static com.carloscuatin.ledgerapp.ledger.adapters.out.persistence.TransferRows.visibleIds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Row level security with nothing but JDBC and the database roles. Every test checks who it is connected as, because a
 * superuser or a role with BYPASSRLS would see everything and an owner without FORCE too, and it checks that the rows
 * it should not see really exist, because seeing no rows proves nothing about an empty table.
 */
class TenantIsolationIT {

    private static final String INSUFFICIENT_PRIVILEGE = "42501";

    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();
    private UUID rowOfA;
    private UUID rowOfB;

    @BeforeEach
    void bothTenantsHaveARow() throws SQLException {
        rowOfA = seed(tenantA);
        rowOfB = seed(tenantB);
        assertThat(idsInTheTable(rowOfA, rowOfB)).containsExactlyInAnyOrder(rowOfA, rowOfB);
    }

    private static Connection connectAsTheApplication() throws SQLException {
        var connection = LedgerDatabase.asApplication();
        assertThat(currentUser(connection)).isEqualTo("ledger_app");
        return connection;
    }

    @Test
    void aQueryWithoutAFilterSeesOnlyTheRowsOfItsTenant() throws SQLException {
        try (var connection = connectAsTheApplication()) {
            connection.setAutoCommit(false);
            setTenant(connection, tenantA);

            assertThat(visibleIds(connection)).containsExactly(rowOfA);
        }
    }

    @Test
    void aQueryWithNoTenantSetSeesNoRows() throws SQLException {
        try (var connection = connectAsTheApplication()) {
            assertThat(visibleIds(connection)).isEmpty();
        }
    }

    @Test
    void theApplicationRoleEscapesNothingAndTheTableForcesItsPolicy() throws SQLException {
        try (var connection = connectAsTheApplication();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("""
                        SELECT r.rolsuper, r.rolbypassrls, t.tableowner, c.relrowsecurity, c.relforcerowsecurity
                        FROM pg_roles r, pg_tables t, pg_class c
                        WHERE r.rolname = current_user
                          AND t.schemaname = 'public' AND t.tablename = 'transfer'
                          AND c.oid = 'public.transfer'::regclass
                        """)) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getBoolean("rolsuper")).isFalse();
            assertThat(rows.getBoolean("rolbypassrls")).isFalse();
            assertThat(rows.getString("tableowner")).isEqualTo("ledger_migrator");
            assertThat(rows.getBoolean("relrowsecurity")).isTrue();
            assertThat(rows.getBoolean("relforcerowsecurity")).isTrue();
        }
    }

    @Test
    void theTableOwnerWithNoTenantSeesNoRows() throws SQLException {
        try (var connection = LedgerDatabase.asMigrator()) {
            assertThat(currentUser(connection)).isEqualTo("ledger_migrator");

            assertThat(visibleIds(connection)).isEmpty();
        }
    }

    @Test
    void aTenantCannotInsertARowForAnotherTenant() throws SQLException {
        var id = UUID.randomUUID();

        try (var connection = connectAsTheApplication()) {
            connection.setAutoCommit(false);
            setTenant(connection, tenantA);

            assertThatThrownBy(() -> insertFor(connection, id, tenantB))
                    .isInstanceOfSatisfying(SQLException.class, failure -> assertThat(failure.getSQLState())
                            .isEqualTo(INSUFFICIENT_PRIVILEGE))
                    .hasMessageContaining("new row violates row-level security policy");
        }

        assertThat(idsInTheTable(id)).isEmpty();
    }

    @Test
    void anInsertWithNoTenantSetAndNoTenantColumnIsRejected() throws SQLException {
        var id = UUID.randomUUID();

        try (var connection = connectAsTheApplication()) {
            assertThatThrownBy(() -> insert(connection, id))
                    .isInstanceOfSatisfying(SQLException.class, failure -> assertThat(failure.getSQLState())
                            .isEqualTo(INSUFFICIENT_PRIVILEGE))
                    .hasMessageContaining("new row violates row-level security policy");
        }

        assertThat(idsInTheTable(id)).isEmpty();
    }

    @Test
    void anInsertWithNoTenantSetThatNamesATenantIsRejected() throws SQLException {
        var id = UUID.randomUUID();

        try (var connection = connectAsTheApplication()) {
            assertThatThrownBy(() -> insertFor(connection, id, tenantA))
                    .isInstanceOfSatisfying(SQLException.class, failure -> assertThat(failure.getSQLState())
                            .isEqualTo(INSUFFICIENT_PRIVILEGE))
                    .hasMessageContaining("new row violates row-level security policy");
        }

        assertThat(idsInTheTable(id)).isEmpty();
    }

    @Test
    void aReusedConnectionKeepsNoTenantAfterItsTransaction() throws SQLException {
        try (var connection = connectAsTheApplication()) {
            connection.setAutoCommit(false);
            setTenant(connection, tenantA);
            assertThat(visibleIds(connection)).containsExactly(rowOfA);
            connection.commit();

            assertThat(visibleIds(connection)).isEmpty();
        }
    }
}
