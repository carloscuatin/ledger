package com.carloscuatin.ledgerapp.ledger.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.carloscuatin.ledgerapp.ledger.application.CurrentTenant;
import com.carloscuatin.ledgerapp.ledger.domain.TenantId;

/**
 * Transactions that are open at the same time, each holding its own connection of a pool of two. Barriers make the
 * overlap certain rather than likely, without depending on timing.
 */
@SpringBootTest(properties = {
    "spring.datasource.hikari.maximum-pool-size=2",
    "spring.datasource.hikari.connection-timeout=5000"
})
class TenantPoolIT {

    private static final int REPETITIONS = 200;
    private static final int BARRIER_TIMEOUT_SECONDS = 10;

    @DynamicPropertySource
    static void connectAsTheApplication(DynamicPropertyRegistry registry) {
        LedgerDatabase.connectAsTheApplication(registry);
    }

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcClient jdbc;

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

    @Test
    void concurrentTenantsOnASharedPoolNeverMix() throws Exception {
        var tenantA = new TenantId(UUID.randomUUID());
        var tenantB = new TenantId(UUID.randomUUID());
        var rowOfA = TransferRows.seed(tenantA.value());
        var rowOfB = TransferRows.seed(tenantB.value());
        assertThat(TransferRows.idsInTheTable(rowOfA, rowOfB)).containsExactlyInAnyOrder(rowOfA, rowOfB);
        var beginTogether = new CyclicBarrier(2);
        var bothOpen = new CyclicBarrier(2);
        var pids = new int[2][REPETITIONS];

        try (var threads = Executors.newFixedThreadPool(2)) {
            var first = threads.submit(repeat(tenantA, rowOfA, beginTogether, bothOpen, pids[0]));
            var second = threads.submit(repeat(tenantB, rowOfB, beginTogether, bothOpen, pids[1]));

            assertThat(first.get()).isEqualTo(REPETITIONS);
            assertThat(second.get()).isEqualTo(REPETITIONS);
        }

        for (var repetition = 0; repetition < REPETITIONS; repetition++) {
            assertThat(pids[0][repetition]).as("connection of repetition %d", repetition)
                    .isNotEqualTo(pids[1][repetition]);
        }
    }

    private Callable<Integer> repeat(TenantId tenant, UUID row, CyclicBarrier beginTogether, CyclicBarrier bothOpen,
            int[] pids) {
        var transaction = new TransactionTemplate(transactionManager);
        return () -> {
            var checked = 0;
            for (var repetition = 0; repetition < REPETITIONS; repetition++) {
                var index = repetition;
                beginTogether.await(BARRIER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                CurrentTenant.callAs(tenant, () -> transaction.execute(status -> {
                    await(bothOpen);
                    pids[index] = backendPid();
                    assertThat(sessionTenant()).as("tenant in repetition %d", index).isEqualTo(tenant.value());
                    assertThat(visibleRows()).as("rows in repetition %d", index).containsExactly(row);
                    // Both stay open until both have read, so each pid belongs to a connection held at the same time.
                    await(bothOpen);
                    return null;
                }));
                checked++;
            }
            return checked;
        };
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(BARRIER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("The other transaction did not arrive", e);
        }
    }

    @Test
    void aNewInnerTransactionHasItsOwnConnectionAndTheSameTenant() {
        var tenant = new TenantId(UUID.randomUUID());
        var outer = new TransactionTemplate(transactionManager);
        var inner = new TransactionTemplate(transactionManager);
        inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        var innerPid = new AtomicInteger();

        CurrentTenant.callAs(tenant, () -> outer.execute(status -> {
            var outerPid = backendPid();
            inner.executeWithoutResult(innerStatus -> {
                innerPid.set(backendPid());
                assertThat(sessionTenant()).isEqualTo(tenant.value());
            });
            assertThat(innerPid.get()).isNotEqualTo(outerPid);
            assertThat(sessionTenant()).isEqualTo(tenant.value());
            return null;
        }));
    }
}
