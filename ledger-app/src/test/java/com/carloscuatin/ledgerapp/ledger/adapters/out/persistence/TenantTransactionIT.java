package com.carloscuatin.ledgerapp.ledger.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.CannotCreateTransactionException;
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
import com.zaxxer.hikari.HikariDataSource;

import jakarta.persistence.EntityManager;

/**
 * With a pool of one connection there is nowhere else for a statement to run: a second connection would wait until the
 * short timeout and fail, and a tenant left behind would be seen by the next user of the only connection.
 */
@SpringBootTest(properties = {
    "spring.datasource.hikari.maximum-pool-size=1",
    "spring.datasource.hikari.connection-timeout=2000"
})
class TenantTransactionIT {

    private static final String NO_TENANT = "No tenant in scope";

    @DynamicPropertySource
    static void connectAsTheApplication(DynamicPropertyRegistry registry) {
        LedgerDatabase.connectAsTheApplication(registry);
    }

    /** Counts what is asked of the pool, which the state of the pool afterwards cannot tell. */
    @TestConfiguration(proxyBeanMethods = false)
    static class CountedConnections {

        static final AtomicInteger REQUESTS = new AtomicInteger();

        @Bean
        static BeanPostProcessor countConnectionRequests() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (!(bean instanceof DataSource dataSource)) {
                        return bean;
                    }
                    return new DelegatingDataSource(dataSource) {
                        @Override
                        public Connection getConnection() throws SQLException {
                            REQUESTS.incrementAndGet();
                            return super.getConnection();
                        }
                    };
                }
            };
        }
    }

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Transfers transfers;

    @Autowired
    private EntityManager entityManager;

    private final TenantId tenantA = new TenantId(UUID.randomUUID());
    private final TenantId tenantB = new TenantId(UUID.randomUUID());

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private TransactionTemplate readOnlyTransaction() {
        var template = transaction();
        template.setReadOnly(true);
        return template;
    }

    private int activeConnections() throws SQLException {
        return dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections();
    }

    private int backendPid() {
        return jdbc.sql("SELECT pg_backend_pid()").query(Integer.class).single();
    }

    private @Nullable UUID sessionTenant() {
        return jdbc.sql("SELECT current_tenant_id()").query((rows, number) -> rows.getObject(1, UUID.class)).list()
                .getFirst();
    }

    private List<UUID> visibleRows() {
        return jdbc.sql("SELECT id FROM transfer").query(UUID.class).list();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aTransactionWithoutATenantDoesNotStart(boolean readOnly) {
        var bodyRan = new AtomicBoolean();
        var template = readOnly ? readOnlyTransaction() : transaction();

        assertThatThrownBy(() -> template.executeWithoutResult(status -> bodyRan.set(true)))
                .isExactlyInstanceOf(CannotCreateTransactionException.class)
                .hasRootCauseExactlyInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage(NO_TENANT);

        assertThat(bodyRan).isFalse();
    }

    @Test
    void aRefusedTransactionAsksThePoolForNoConnectionAndHoldsNone() throws SQLException {
        var requests = CountedConnections.REQUESTS.get();

        for (var attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> transaction().executeWithoutResult(status -> {
            })).isInstanceOf(CannotCreateTransactionException.class);
            assertThat(activeConnections()).isZero();
        }

        assertThat(CountedConnections.REQUESTS.get()).isEqualTo(requests);
        UUID tenant = CurrentTenant.callAs(tenantA, () -> transaction().execute(status -> sessionTenant()));
        assertThat(tenant).isEqualTo(tenantA.value());
    }

    @Test
    void theTenantDoesNotSurviveACommitOnAReusedConnection() throws SQLException {
        var row = TransferRows.seed(tenantA.value());
        assertThat(TransferRows.idsInTheTable(row)).containsExactly(row);
        var seen = new AtomicInteger();

        CurrentTenant.callAs(tenantA, () -> transaction().execute(status -> {
            assertThat(jdbc.sql("SELECT current_user").query(String.class).single()).isEqualTo("ledger_app");
            assertThat(sessionTenant()).isEqualTo(tenantA.value());
            assertThat(visibleRows()).containsExactly(row);
            seen.set(backendPid());
            return null;
        }));

        assertThat(backendPid()).isEqualTo(seen.get());
        assertThat(sessionTenant()).isNull();
        assertThat(visibleRows()).isEmpty();
    }

    @Test
    void theTenantDoesNotSurviveARollbackOnAReusedConnection() throws SQLException {
        var row = TransferRows.seed(tenantA.value());
        assertThat(TransferRows.idsInTheTable(row)).containsExactly(row);
        var seen = new AtomicInteger();

        CurrentTenant.callAs(tenantA, () -> transaction().execute(status -> {
            assertThat(visibleRows()).containsExactly(row);
            seen.set(backendPid());
            status.setRollbackOnly();
            return null;
        }));

        assertThat(backendPid()).isEqualTo(seen.get());
        assertThat(sessionTenant()).isNull();
        assertThat(visibleRows()).isEmpty();
    }

    @Test
    void twoTenantsInTurnOnASingleConnectionSeeOnlyTheirRows() throws SQLException {
        var rowOfA = TransferRows.seed(tenantA.value());
        var rowOfB = TransferRows.seed(tenantB.value());
        assertThat(TransferRows.idsInTheTable(rowOfA, rowOfB)).containsExactlyInAnyOrder(rowOfA, rowOfB);
        var pid = backendPid();

        assertRowsInATransaction(tenantA, rowOfA, pid);
        assertRowsInATransaction(tenantB, rowOfB, pid);
        assertRowsInATransaction(tenantA, rowOfA, pid);
    }

    @Test
    void aReadOnlyTransactionCarriesItsTenantToo() throws SQLException {
        var row = TransferRows.seed(tenantA.value());

        var visible = CurrentTenant.callAs(tenantA, () -> readOnlyTransaction().execute(status -> visibleRows()));

        assertThat(visible).containsExactly(row);
    }

    @Test
    void aWriteThroughJpaSharesItsConnectionAndTenantWithJdbcOnceFlushed() throws SQLException {
        var hold = new PendingTransfer(new TransferId(UUID.randomUUID()), new TransferTerms(
                new AccountId(UUID.randomUUID()), new AccountId(UUID.randomUUID()),
                Money.parse("1", Currency.getInstance("JPY"))));
        var row = hold.id().value();
        var pid = new AtomicInteger();

        CurrentTenant.callAs(tenantA, () -> transaction().execute(status -> {
            transfers.add(hold);
            // Nothing has reached the database yet, and JDBC does not make JPA flush.
            assertThat(visibleRows()).isEmpty();

            entityManager.flush();

            pid.set(backendPid());
            assertThat(sessionTenant()).isEqualTo(tenantA.value());
            assertThat(visibleRows()).containsExactly(row);
            assertThatTheControlFinds(row, false);
            return null;
        }));

        assertThatTheControlFinds(row, true);
        assertThat(backendPid()).isEqualTo(pid.get());
    }

    /** The control has a connection of its own, so it only sees what has been committed. */
    private static void assertThatTheControlFinds(UUID row, boolean found) {
        try {
            assertThat(TransferRows.idsInTheTable(row).contains(row)).isEqualTo(found);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void assertRowsInATransaction(TenantId tenant, UUID row, int pid) {
        CurrentTenant.callAs(tenant, () -> transaction().execute(status -> {
            assertThat(backendPid()).isEqualTo(pid);
            assertThat(visibleRows()).containsExactly(row);
            return null;
        }));
    }
}
