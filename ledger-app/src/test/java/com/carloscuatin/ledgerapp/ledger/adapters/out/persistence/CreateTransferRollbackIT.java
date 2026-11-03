package com.carloscuatin.ledgerapp.ledger.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.Currency;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.carloscuatin.ledgerapp.ledger.application.CreateTransfer;
import com.carloscuatin.ledgerapp.ledger.application.CurrentTenant;
import com.carloscuatin.ledgerapp.ledger.application.Transfers;
import com.carloscuatin.ledgerapp.ledger.domain.AccountId;
import com.carloscuatin.ledgerapp.ledger.domain.Money;
import com.carloscuatin.ledgerapp.ledger.domain.TenantId;
import com.carloscuatin.ledgerapp.ledger.domain.TransferTerms;

import jakarta.persistence.EntityManager;

/**
 * A failure after the row reached the database. Without the flush the INSERT would never be sent, and the row would be
 * missing even with no rollback at all, so the failing port also confirms the row is there before it fails.
 */
@SpringBootTest
class CreateTransferRollbackIT {

    private static final RuntimeException FAILURE = new IllegalStateException("Failed after storing");
    private static final AtomicReference<UUID> STORED = new AtomicReference<>();
    private static final AtomicReference<UUID> VISIBLE_BEFORE_FAILING = new AtomicReference<>();

    @DynamicPropertySource
    static void connectAsTheApplication(DynamicPropertyRegistry registry) {
        LedgerDatabase.connectAsTheApplication(registry);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingAfterStoring {

        @Bean
        @Primary
        Transfers failingTransfers(@Qualifier("transfers") Transfers real, EntityManager entityManager,
                JdbcClient jdbc) {
            return hold -> {
                real.add(hold);
                entityManager.flush();
                STORED.set(hold.id().value());
                VISIBLE_BEFORE_FAILING.set(jdbc.sql("SELECT id FROM transfer WHERE id = ?")
                        .param(hold.id().value())
                        .query(UUID.class)
                        .optional()
                        .orElse(null));
                throw FAILURE;
            };
        }
    }

    @Autowired
    private CreateTransfer createTransfer;

    @Test
    void aFailureAfterTheInsertReachedTheDatabaseLeavesNothing() throws SQLException {
        var tenant = new TenantId(UUID.randomUUID());
        var pesos = new TransferTerms(new AccountId(UUID.randomUUID()), new AccountId(UUID.randomUUID()),
                Money.parse("150000.00", Currency.getInstance("COP")));

        assertThatThrownBy(() -> CurrentTenant.callAs(tenant, () -> createTransfer.create(pesos))).isSameAs(FAILURE);

        var stored = STORED.get();
        assertThat(stored).isNotNull();
        assertThat(VISIBLE_BEFORE_FAILING.get()).isEqualTo(stored);
        assertThat(TransferRows.idsInTheTable(stored)).isEmpty();
    }
}
