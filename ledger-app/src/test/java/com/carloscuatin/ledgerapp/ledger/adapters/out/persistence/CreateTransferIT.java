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
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.IllegalTransactionStateException;

import com.carloscuatin.ledgerapp.ledger.application.CreateTransfer;
import com.carloscuatin.ledgerapp.ledger.application.CurrentTenant;
import com.carloscuatin.ledgerapp.ledger.application.Transfers;
import com.carloscuatin.ledgerapp.ledger.domain.AccountId;
import com.carloscuatin.ledgerapp.ledger.domain.CurrencyNotEnabledException;
import com.carloscuatin.ledgerapp.ledger.domain.Money;
import com.carloscuatin.ledgerapp.ledger.domain.PendingTransfer;
import com.carloscuatin.ledgerapp.ledger.domain.TenantId;
import com.carloscuatin.ledgerapp.ledger.domain.TransferId;
import com.carloscuatin.ledgerapp.ledger.domain.TransferStatus;
import com.carloscuatin.ledgerapp.ledger.domain.TransferTerms;

/**
 * What creating a transfer leaves in the table, read with SQL as a superuser so the check goes through neither the
 * mapping nor row level security.
 */
@SpringBootTest(properties = "ledger.currencies=COP,JPY")
class CreateTransferIT {

    private static final Currency COP = Currency.getInstance("COP");
    private static final Currency JPY = Currency.getInstance("JPY");
    private static final Currency USD = Currency.getInstance("USD");

    @DynamicPropertySource
    static void connectAsTheApplication(DynamicPropertyRegistry registry) {
        LedgerDatabase.connectAsTheApplication(registry);
    }

    @Autowired
    private CreateTransfer createTransfer;

    @Autowired
    private Transfers transfers;

    private final TenantId tenant = new TenantId(UUID.randomUUID());
    private final UUID source = UUID.randomUUID();
    private final UUID destination = UUID.randomUUID();

    private TransferTerms terms(String amount, Currency currency) {
        return new TransferTerms(new AccountId(source), new AccountId(destination), Money.parse(amount, currency));
    }

    private PendingTransfer create(String amount, Currency currency) {
        return CurrentTenant.callAs(tenant, () -> createTransfer.create(terms(amount, currency)));
    }

    /** No test here commits a row for this source account unless it means to, whichever identifier the row got. */
    private int rowsOfTheSourceAccount() throws SQLException {
        try (var connection = LedgerDatabase.asSuperuser();
                var statement = connection.prepareStatement(
                        "SELECT count(*) FROM transfer WHERE source_account_id = ?")) {
            statement.setObject(1, source);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
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
        var hold = create("150000.00", COP);

        assertThat(hold.status()).isEqualTo(TransferStatus.PENDING);
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
        var hold = create("150000.00", COP);

        assertRow(hold, row -> assertThat(row.getObject("tenant_id", UUID.class)).isEqualTo(tenant.value()));
    }

    @Test
    void theAmountKeepsTheScaleOfItsCurrency() throws SQLException {
        var hold = create("100", JPY);

        assertRow(hold, row -> {
            assertThat(row.getLong("amount_minor")).isEqualTo(100L);
            assertThat(row.getString("currency")).isEqualTo("JPY");
        });
    }

    @Test
    void storingOutsideATransactionIsRefused() throws SQLException {
        var hold = new PendingTransfer(new TransferId(UUID.randomUUID()), terms("150000.00", COP));

        assertThatThrownBy(() -> CurrentTenant.callAs(tenant, () -> {
            transfers.add(hold);
            return null;
        })).isExactlyInstanceOf(IllegalTransactionStateException.class);

        assertThat(TransferRows.idsInTheTable(hold.id().value())).isEmpty();
    }

    @Test
    void aRejectedCurrencyStoresNothing() throws SQLException {
        var dollars = terms("100.00", USD);

        assertThatThrownBy(() -> CurrentTenant.callAs(tenant, () -> createTransfer.create(dollars)))
                .isExactlyInstanceOf(CurrencyNotEnabledException.class);

        assertThat(rowsOfTheSourceAccount()).isZero();
    }

    @Test
    void creatingATransferWithoutATenantStoresNothing() throws SQLException {
        var pesos = terms("150000.00", COP);

        assertThatThrownBy(() -> createTransfer.create(pesos))
                .isExactlyInstanceOf(CannotCreateTransactionException.class)
                .hasRootCauseExactlyInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("No tenant in scope");

        assertThat(rowsOfTheSourceAccount()).isZero();
    }
}
