package com.carloscuatin.ledgerapp.ledger.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The table rejects what the domain would reject, even when a statement never went through the domain. Statements run
 * as the application role, in a transaction of a new tenant, and are never committed.
 */
class TransferSchemaIT {

    private static final String CHECK_VIOLATION = "23514";

    private final UUID source = UUID.randomUUID();
    private final UUID destination = UUID.randomUUID();

    @Test
    void aValidRowIsAccepted() {
        assertThatCode(() -> insert(source, destination, 1, "PENDING")).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void aNonPositiveAmountIsRejected(long amount) {
        assertThatThrownBy(() -> insert(source, destination, amount, "PENDING"))
                .isInstanceOfSatisfying(SQLException.class, TransferSchemaIT::isACheckViolation)
                .hasMessageContaining("transfer_amount_minor_check");
    }

    @Test
    void theSameAccountOnBothSidesIsRejected() {
        assertThatThrownBy(() -> insert(source, source, 1, "PENDING"))
                .isInstanceOfSatisfying(SQLException.class, TransferSchemaIT::isACheckViolation)
                .hasMessageContaining("transfer_check");
    }

    @Test
    void anUnknownStatusIsRejected() {
        assertThatThrownBy(() -> insert(source, destination, 1, "UNKNOWN"))
                .isInstanceOfSatisfying(SQLException.class, TransferSchemaIT::isACheckViolation)
                .hasMessageContaining("transfer_status_check");
    }

    private static void isACheckViolation(SQLException failure) {
        assertThat(failure.getSQLState()).isEqualTo(CHECK_VIOLATION);
    }

    private static void insert(UUID from, UUID to, long amount, String status) throws SQLException {
        try (var connection = LedgerDatabase.asApplication()) {
            connection.setAutoCommit(false);
            try (var tenant = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
                tenant.setString(1, UUID.randomUUID().toString());
                tenant.execute();
            }
            try (var insert = connection.prepareStatement("""
                    INSERT INTO transfer (id, source_account_id, destination_account_id, amount_minor, currency, status)
                    VALUES (?, ?, ?, ?, 'COP', ?)
                    """)) {
                insert.setObject(1, UUID.randomUUID());
                insert.setObject(2, from);
                insert.setObject(3, to);
                insert.setLong(4, amount);
                insert.setString(5, status);
                insert.executeUpdate();
            } finally {
                connection.rollback();
            }
        }
    }
}
