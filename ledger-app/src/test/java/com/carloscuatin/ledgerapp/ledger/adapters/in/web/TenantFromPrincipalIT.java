package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import static com.carloscuatin.ledgerapp.ledger.adapters.in.web.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpRequest.BodyPublishers;
import java.util.UUID;

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

    private static UUID idIn(String body) {
        return UUID.fromString(JSON.readTree(body).path("id").asString());
    }
}
