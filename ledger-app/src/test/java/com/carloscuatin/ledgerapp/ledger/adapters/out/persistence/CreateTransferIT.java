package com.carloscuatin.ledgerapp.ledger.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Currency;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.carloscuatin.ledgerapp.ledger.application.CurrentTenant;
import com.carloscuatin.ledgerapp.ledger.application.Transfers;
import com.carloscuatin.ledgerapp.ledger.domain.AccountId;
import com.carloscuatin.ledgerapp.ledger.domain.Money;
import com.carloscuatin.ledgerapp.ledger.domain.PendingTransfer;
import com.carloscuatin.ledgerapp.ledger.domain.TenantId;
import com.carloscuatin.ledgerapp.ledger.domain.TransferId;
import com.carloscuatin.ledgerapp.ledger.domain.TransferTerms;

/**
 * What storing a transfer leaves in the table, read with SQL as a superuser so the check goes through neither the
 * mapping nor row level security.
 */
@SpringBootTest
class CreateTransferIT {

    private static final Currency COP = Currency.getInstance("COP");
    private static final Currency JPY = Currency.getInstance("JPY");

    @DynamicPropertySource
    static void connectAsTheApplication(DynamicPropertyRegistry registry) {
        LedgerDatabase.connectAsTheApplication(registry);
    }

    @Autowired
    private Transfers transfers;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final TenantId tenant = new TenantId(UUID.randomUUID());
    private final UUID source = UUID.randomUUID();
    private final UUID destination = UUID.randomUUID();

    private PendingTransfer hold(String amount, Currency currency) {
        return new PendingTransfer(new TransferId(UUID.randomUUID()),
                new TransferTerms(new AccountId(source), new AccountId(destination), Money.parse(amount, currency)));
    }

    private void store(PendingTransfer hold) {
        var transaction = new TransactionTemplate(transactionManager);
        CurrentTenant.callAs(tenant, () -> {
            transaction.executeWithoutResult(status -> transfers.add(hold));
            return null;
        });
    }

    private interface RowCheck {
        void check(ResultSet row) throws SQLException;
    }

    private static void assertRow(PendingTransfer hold, RowCheck check) throws SQLException {
        try (var connection = LedgerDatabase.asSuperuser();
                var statement = connection.prepareStatement("SELECT * FROM transfer WHERE id = ?")) {
            statement.setObject(1, hold.id().value());
            try (var row = statement.executeQuery()) {
                assertThat(row.next()).isTrue();
                check.check(row);
            }
        }
    }

    @Test
    void theHoldIsStoredWithItsTermsAndStatus() throws SQLException {
        var hold = hold("150000.00", COP);

        store(hold);

        assertRow(hold, row -> {
            assertThat(row.getObject("source_account_id", UUID.class)).isEqualTo(source);
            assertThat(row.getObject("destination_account_id", UUID.class)).isEqualTo(destination);
            assertThat(row.getLong("amount_minor")).isEqualTo(15000000L);
            assertThat(row.getString("currency")).isEqualTo("COP");
            assertThat(row.getString("status")).isEqualTo("PENDING");
        });
    }

    @Test
    void theRowTakesItsTenantFromTheTransaction() throws SQLException {
        var hold = hold("150000.00", COP);

        store(hold);

        assertRow(hold, row -> assertThat(row.getObject("tenant_id", UUID.class)).isEqualTo(tenant.value()));
    }

    @Test
    void theAmountKeepsTheScaleOfItsCurrency() throws SQLException {
        var hold = hold("100", JPY);

        store(hold);

        assertRow(hold, row -> {
            assertThat(row.getLong("amount_minor")).isEqualTo(100L);
            assertThat(row.getString("currency")).isEqualTo("JPY");
        });
    }

    @Test
    void storingOutsideATransactionIsRefused() throws SQLException {
        var hold = hold("150000.00", COP);

        assertThatThrownBy(() -> CurrentTenant.callAs(tenant, () -> {
            transfers.add(hold);
            return null;
        })).isExactlyInstanceOf(IllegalTransactionStateException.class);

        assertThat(TransferRows.idsInTheTable(hold.id().value())).isEmpty();
    }
}
