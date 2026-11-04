package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import static com.carloscuatin.ledgerapp.ledger.adapters.in.web.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpRequest.BodyPublishers;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.carloscuatin.ledgerapp.ledger.adapters.out.persistence.TransferRows;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Row level security filters by whatever tenant it is told, so a wrong tenant raises no error. These tests therefore
 * look at the table as the superuser, past row level security, instead of trusting what the application answers.
 */
class TenantFromPrincipalIT extends AuthenticatedApi {

    private static final String TRANSFERS = "/v1/transfers";
    private static final int ITERATIONS = 200;

    @Autowired
    private HikariDataSource pool;

    @Test
    void aStoredTransferBelongsToTheTenantOfThePrincipal() throws Exception {
        var tenant = UUID.randomUUID();

        var response = send(postingJson(requestAs(tenant, TRANSFERS), aValidBody()));

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(TransferRows.tenantOf(idIn(response.body()))).isEqualTo(tenant);
    }

    @Test
    void aTenantInTheRequestDoesNotWinOverThePrincipal() throws Exception {
        var tenant = UUID.randomUUID();
        var other = UUID.randomUUID();

        var response = send(postingJson(requestAs(tenant, TRANSFERS + "?tenant_id=" + other), aValidBody())
                .header("X-Tenant-Id", other.toString()));

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(TransferRows.tenantOf(idIn(response.body()))).isEqualTo(tenant);
        assertThat(TransferRows.countInTheTableFor(other)).isZero();
    }

    /** Whether its work runs at all is the concern of {@link AuthenticatedTenantTest}: an idle pool cannot tell. */
    @Test
    void aPrincipalWithoutATenantStoresNothingAndHoldsNoConnection() throws Exception {
        var rowsBefore = TransferRows.countInTheTable();

        var response = send(postingJson(requestAs(TestAuthentication.NO_TENANT, TRANSFERS), aValidBody()));

        assertProblem(response, ProblemType.TENANT_REQUIRED, TRANSFERS);
        assertThat(TransferRows.countInTheTable()).isEqualTo(rowsBefore);
        assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
    }

    /** The tenant comes first: without one, whether a route has something to serve is nothing this principal may learn. */
    @Test
    void aPrincipalWithoutATenantCannotList() throws Exception {
        var rowsBefore = TransferRows.countInTheTable();

        var response = send(requestAs(TestAuthentication.NO_TENANT, TRANSFERS));

        assertProblem(response, ProblemType.TENANT_REQUIRED, TRANSFERS);
        assertThat(TransferRows.countInTheTable()).isEqualTo(rowsBefore);
        assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
    }

    @Test
    void aPrincipalWithoutATenantIsToldSoBeforeAboutItsCursor() throws Exception {
        var response = send(requestAs(TestAuthentication.NO_TENANT, TRANSFERS + "?cursor=SYNTHETICMARK"));

        assertProblem(response, ProblemType.TENANT_REQUIRED, TRANSFERS);
        assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
    }

    @Test
    void aPrincipalWithoutATenantCannotReadATransferWhetherItExistsOrNot() throws Exception {
        var existing = TRANSFERS + "/" + TransferRows.seed(UUID.randomUUID());
        var unknown = TRANSFERS + "/" + UUID.randomUUID();
        var rowsBefore = TransferRows.countInTheTable();

        assertProblem(send(requestAs(TestAuthentication.NO_TENANT, existing)), ProblemType.TENANT_REQUIRED, existing);
        assertProblem(send(requestAs(TestAuthentication.NO_TENANT, unknown)), ProblemType.TENANT_REQUIRED, unknown);
        assertThat(TransferRows.countInTheTable()).isEqualTo(rowsBefore);
        assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void aPrincipalWithoutATenantIsNotAnErrorInTheLog(CapturedOutput output) throws Exception {
        var response = send(postingJson(requestAs(TestAuthentication.NO_TENANT, TRANSFERS), aValidBody()));

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(output).doesNotContain(" ERROR ").doesNotContain(" WARN ").doesNotContain("Exception");
    }

    @Test
    void aTenantFieldInTheBodyIsRefused() throws Exception {
        var tenant = UUID.randomUUID();
        var other = UUID.randomUUID();

        var response = send(postingJson(requestAs(tenant, TRANSFERS), BodyPublishers.ofString("""
                {"sourceAccountId": "%s", "destinationAccountId": "%s", "amount": "150000.00", "currency": "COP",
                 "tenantId": "%s"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), other))));

        assertProblem(response, ProblemType.MALFORMED_REQUEST, TRANSFERS);
        assertThat(response.body()).doesNotContain(other.toString());
        assertThat(TransferRows.countInTheTableFor(tenant)).isZero();
        assertThat(TransferRows.countInTheTableFor(other)).isZero();
    }

    /**
     * Pairs of tenants in step, so that each request overlaps with the others on the threads and connections they share.
     * Whatever row a list shows, the table says whose it is; a mix-up would raise no error anywhere else.
     */
    @Test
    void requestsInParallelNeverSeeEachOthersTenant() throws Exception {
        var tenants = List.of(UUID.randomUUID(), UUID.randomUUID());
        var listed = new ConcurrentHashMap<UUID, Set<UUID>>();
        var created = new ConcurrentHashMap<UUID, Set<UUID>>();
        for (var tenant : tenants) {
            listed.put(tenant, ConcurrentHashMap.newKeySet());
            created.put(tenant, ConcurrentHashMap.newKeySet());
        }

        try (var requests = Executors.newVirtualThreadPerTaskExecutor()) {
            for (var iteration = 0; iteration < ITERATIONS; iteration++) {
                var inStep = new ArrayList<Callable<Void>>();
                for (var tenant : tenants) {
                    inStep.add(() -> {
                        var response = send(postingJson(requestAs(tenant, TRANSFERS), aValidBody()));
                        assertThat(response.statusCode()).isEqualTo(201);
                        created.get(tenant).add(idIn(response.body()));
                        return null;
                    });
                    inStep.add(() -> {
                        var response = send(requestAs(tenant, TRANSFERS + "?limit=100"));
                        assertThat(response.statusCode()).isEqualTo(200);
                        for (var item : JSON.readTree(response.body()).path("items")) {
                            listed.get(tenant).add(UUID.fromString(item.path("id").asString()));
                        }
                        return null;
                    });
                }
                for (var request : requests.invokeAll(inStep)) {
                    request.get();
                }
            }
        }

        for (var tenant : tenants) {
            assertThat(created.get(tenant)).hasSize(ITERATIONS);
            assertThat(TransferRows.idsNewestFirst(tenant)).containsExactlyInAnyOrderElementsOf(created.get(tenant));
            assertThat(listed.get(tenant)).isNotEmpty().isSubsetOf(created.get(tenant));
        }
    }

    private static UUID idIn(String body) {
        return UUID.fromString(JSON.readTree(body).path("id").asString());
    }
}
