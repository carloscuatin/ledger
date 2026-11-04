package com.carloscuatin.ledgerapp.ledger.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Currency;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import org.aopalliance.intercept.MethodInterceptor;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.carloscuatin.ledgerapp.ledger.application.CreateTransfer;
import com.carloscuatin.ledgerapp.ledger.application.CurrentTenant;
import com.carloscuatin.ledgerapp.ledger.application.FindTransfer;
import com.carloscuatin.ledgerapp.ledger.application.TransferQueries;
import com.carloscuatin.ledgerapp.ledger.application.TransferView;
import com.carloscuatin.ledgerapp.ledger.domain.AccountId;
import com.carloscuatin.ledgerapp.ledger.domain.Money;
import com.carloscuatin.ledgerapp.ledger.domain.PendingTransfer;
import com.carloscuatin.ledgerapp.ledger.domain.TenantId;
import com.carloscuatin.ledgerapp.ledger.domain.TransferId;
import com.carloscuatin.ledgerapp.ledger.domain.TransferNotFoundException;
import com.carloscuatin.ledgerapp.ledger.domain.TransferStatus;
import com.carloscuatin.ledgerapp.ledger.domain.TransferTerms;

/**
 * Reading as the application role, on a real database. A pool of one connection leaves nowhere else for a statement to
 * run, so a read that took another connection would wait until the short timeout and fail, and a read only setting left
 * on the connection would reach the next user of the only one.
 */
@SpringBootTest(properties = {
    "spring.datasource.hikari.maximum-pool-size=1",
    "spring.datasource.hikari.connection-timeout=2000"
})
class TransferQueriesIT {

    private static final String READ_ONLY_SQL_TRANSACTION = "25006";
    private static final Currency COP = Currency.getInstance("COP");

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
    private FindTransfer findTransfer;

    @Autowired
    private CreateTransfer createTransfer;

    @Autowired
    private TransferQueries queries;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final TenantId tenantA = new TenantId(UUID.randomUUID());
    private final TenantId tenantB = new TenantId(UUID.randomUUID());

    private static TransferTerms someTerms() {
        return new TransferTerms(new AccountId(UUID.randomUUID()), new AccountId(UUID.randomUUID()),
                Money.parse("150000.00", COP));
    }

    private PendingTransfer createAs(TenantId tenant) {
        return CurrentTenant.callAs(tenant, () -> createTransfer.create(someTerms()));
    }

    private TransferView findAs(TenantId tenant, TransferId id) {
        return CurrentTenant.callAs(tenant, () -> findTransfer.find(id));
    }

    @Test
    void aReadInAScopeSeesOnlyItsTenantWithTheValuesTheCreationWrote() {
        var ofA = createAs(tenantA);
        var ofB = createAs(tenantB);

        assertThat(findAs(tenantA, ofA.id())).isEqualTo(new TransferView(ofA.id(), ofA.terms(), ofA.status()));
        assertThat(findAs(tenantB, ofB.id())).isEqualTo(new TransferView(ofB.id(), ofB.terms(), ofB.status()));
        assertThatThrownBy(() -> findAs(tenantA, ofB.id())).isExactlyInstanceOf(TransferNotFoundException.class);
        assertThatThrownBy(() -> findAs(tenantB, ofA.id())).isExactlyInstanceOf(TransferNotFoundException.class);
    }

    /** The control shows the row is there, so the refusal above cannot be a row that was never stored. */
    @Test
    void aTransferOfAnotherTenantIsInTheTable() throws SQLException {
        var ofB = createAs(tenantB);

        assertThat(TransferRows.tenantOf(ofB.id().value())).isEqualTo(tenantB.value());
        assertThatThrownBy(() -> findAs(tenantA, ofB.id())).isExactlyInstanceOf(TransferNotFoundException.class);
    }

    /** A read reports the state a row is in, whichever use case wrote it. */
    @ParameterizedTest
    @EnumSource(TransferStatus.class)
    void everyStatusTheSchemaAllowsIsReadAsItIs(TransferStatus status) throws SQLException {
        var row = TransferRows.seedWithStatus(tenantA.value(), status.name());

        var view = findAs(tenantA, new TransferId(row));

        assertThat(view.status()).isEqualTo(status);
    }

    @Test
    void aReadOutsideAScopeIsRefusedWithoutAskingThePool() {
        var id = new TransferId(UUID.randomUUID());
        var requests = CountedConnections.REQUESTS.get();

        assertThatThrownBy(() -> findTransfer.find(id))
                .isExactlyInstanceOf(CannotCreateTransactionException.class)
                .hasRootCauseExactlyInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("No tenant in scope");

        assertThat(CountedConnections.REQUESTS.get()).isEqualTo(requests);
    }

    @Test
    void theAdapterRefusesToRunWithoutATransaction() {
        var ofA = createAs(tenantA);

        assertThatThrownBy(() -> CurrentTenant.callAs(tenantA, () -> queries.find(ofA.id())))
                .isExactlyInstanceOf(IllegalTransactionStateException.class)
                .hasMessageContaining("mandatory");
    }

    @Test
    void aReadOnlyTransactionCarriesTheTenantAndRefusesAWrite() {
        var seenTenant = new AtomicReference<@Nullable UUID>();
        var template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        var insert = """
                INSERT INTO transfer (id, source_account_id, destination_account_id, amount_minor, currency, status)
                VALUES (?, ?, ?, 1, 'COP', 'PENDING')
                """;

        assertThatThrownBy(() -> CurrentTenant.callAs(tenantA, () -> template.execute(status -> {
            seenTenant.set(jdbc.sql("SELECT current_tenant_id()").query(UUID.class).single());
            return jdbc.sql(insert).params(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()).update();
        }))).isInstanceOfSatisfying(DataAccessException.class, failure -> {
            assertThat(failure.getRootCause()).isInstanceOfSatisfying(SQLException.class,
                    cause -> assertThat(cause.getSQLState()).isEqualTo(READ_ONLY_SQL_TRANSACTION));
        });

        assertThat(seenTenant).hasValue(tenantA.value());
    }

    /**
     * Asked of the database inside the port, so it measures the transaction {@link FindTransfer} really opens. The
     * annotation on the use case is all that decides this, and no test without a transaction manager could see it.
     */
    @Test
    void theUseCaseReadsInATransactionTheDatabaseTreatsAsReadOnly() {
        var ofA = createAs(tenantA);
        var seen = new AtomicReference<@Nullable String>();
        MethodInterceptor askTheDatabase = invocation -> {
            seen.set(jdbc.sql("SHOW transaction_read_only").query(String.class).single());
            return invocation.proceed();
        };
        var port = (Advised) queries;
        port.addAdvice(0, askTheDatabase);
        try {
            findAs(tenantA, ofA.id());
        } finally {
            port.removeAdvice(askTheDatabase);
        }

        assertThat(seen).hasValue("on");
    }

    /**
     * A write that joins a read only transaction is lost without an error, because the session it runs on never flushes.
     * This looks at the other way the two could meet: the only connection, right after a read.
     */
    @Test
    void aWriteOnTheOnlyConnectionAfterAReadIsStored() throws SQLException {
        var earlier = createAs(tenantA);
        findAs(tenantA, earlier.id());
        var pidOfTheRead = jdbc.sql("SELECT pg_backend_pid()").query(Integer.class).single();

        var hold = createAs(tenantA);

        assertThat(TransferRows.idsInTheTable(hold.id().value())).containsExactly(hold.id().value());
        assertThat(jdbc.sql("SELECT pg_backend_pid()").query(Integer.class).single()).isEqualTo(pidOfTheRead);
        assertThat(jdbc.sql("SHOW transaction_read_only").query(String.class).single()).isEqualTo("off");
    }
}
