package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.carloscuatin.ledgerapp.ledger.adapters.out.persistence.TransferRows;

/**
 * The version lives in the path so that a firewall and the access logs can see it, which only holds if the one spelling
 * they see is the only one that reaches the application. A request that creates a row is the proof that it got there.
 */
class VersioningIT extends AuthenticatedApi {

    @Test
    void theSupportedVersionReachesTheApi() throws Exception {
        var tenant = UUID.randomUUID();

        var response = send(postingJson(requestAs(tenant, "/v1/transfers"), aValidBody()));

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(TransferRows.countInTheTableFor(tenant)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/transfers", "/1/transfers", "/v1.0/transfers", "/v01/transfers", "/V1/transfers",
        "/vx/transfers", "/v0/transfers"})
    void otherSpellingsOfTheVersionDoNotExist(String path) throws Exception {
        var tenant = UUID.randomUUID();

        var created = send(postingJson(requestAs(tenant, path), aValidBody()));
        var listed = send(requestAs(tenant, path));

        assertThat(created.statusCode()).isEqualTo(404);
        assertThat(listed.statusCode()).isEqualTo(404);
        assertThat(TransferRows.countInTheTableFor(tenant)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v2/transfers", "/v99999999999/transfers"})
    void aWellFormedVersionWithoutSupportIsRefused(String path) throws Exception {
        var tenant = UUID.randomUUID();

        var created = send(postingJson(requestAs(tenant, path), aValidBody()));
        var listed = send(requestAs(tenant, path));

        assertThat(created.statusCode()).isEqualTo(400);
        assertThat(listed.statusCode()).isEqualTo(400);
        assertThat(TransferRows.countInTheTableFor(tenant)).isZero();
    }

    @Test
    void theErrorPageIsNotReadAsAVersion() throws Exception {
        var response = send(requestAs(UUID.randomUUID(), "/error"));

        assertThat(response.body()).doesNotContain("Invalid API version");
        assertThat(response.statusCode()).isNotEqualTo(400);
    }

    /** A failure is forwarded to the error page, and that forward must not turn into a complaint about the version. */
    @ParameterizedTest
    @CsvSource({"/v1/nope, 404", "/v1/transfers;x=1, 400", "/v1/transfers/not-a-uuid, 400"})
    void aFailureKeepsItsStatusThroughTheErrorPage(String path, int status) throws Exception {
        var response = send(requestAs(UUID.randomUUID(), path));

        assertThat(response.body()).doesNotContain("Invalid API version");
        assertThat(response.statusCode()).isEqualTo(status);
    }
}
