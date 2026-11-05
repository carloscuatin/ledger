package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import static com.carloscuatin.ledgerapp.ledger.adapters.in.web.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.carloscuatin.ledgerapp.ledger.adapters.out.persistence.TransferRows;

class CreateTransferApiIT extends AuthenticatedApi {

    private static final String TRANSFERS = "/v1/transfers";

    @Test
    void aValidRequestCreatesTheHold() throws Exception {
        var source = UUID.randomUUID();
        var destination = UUID.randomUUID();
        var body = """
                {"sourceAccountId": "%s", "destinationAccountId": "%s", "amount": "150000.00", "currency": "COP"}
                """.formatted(source, destination);

        var response = send(postingJson(requestAs(UUID.randomUUID(), TRANSFERS), BodyPublishers.ofString(body)));

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/json");
        var created = JSON.readTree(response.body());
        var id = UUID.fromString(created.path("id").asString());
        assertThat(id.version()).isEqualTo(7);
        assertThat(created.path("status").asString()).isEqualTo("PENDING");
        assertThat(created.path("sourceAccountId").asString()).isEqualTo(source.toString());
        assertThat(created.path("destinationAccountId").asString()).isEqualTo(destination.toString());
        assertThat(created.path("amount").asString()).isEqualTo("150000.00");
        assertThat(created.path("currency").asString()).isEqualTo("COP");
        assertThat(response.headers().firstValue("Location")).hasValue(TRANSFERS + "/" + id);
        assertThat(TransferRows.amountMinorOf(id)).isEqualTo(15_000_000L);
    }

    @Test
    void theLocationLeadsToTheSameTransfer() throws Exception {
        var tenant = UUID.randomUUID();
        var created = send(postingJson(requestAs(tenant, TRANSFERS), aValidBody()));
        assertThat(created.statusCode()).isEqualTo(201);
        var location = created.headers().firstValue("Location").orElseThrow();

        var followed = send(requestAs(tenant, location));

        assertThat(followed.statusCode()).isEqualTo(200);
        assertThat(followed.headers().firstValue("Content-Type"))
                .isEqualTo(created.headers().firstValue("Content-Type"));
        assertThat(followed.body()).isEqualTo(created.body());
    }

    @Test
    void theLocationLeadsNowhereForAnotherTenant() throws Exception {
        var created = send(postingJson(requestAs(UUID.randomUUID(), TRANSFERS), aValidBody()));
        var location = created.headers().firstValue("Location").orElseThrow();

        assertProblem(send(requestAs(UUID.randomUUID(), location)), ProblemType.NOT_FOUND, location);
    }

    @Test
    void theClientCannotChooseTheIdentifier() throws Exception {
        var tenant = UUID.randomUUID();
        var chosen = UUID.randomUUID();

        var response = send(postingJson(requestAs(tenant, TRANSFERS), BodyPublishers.ofString("""
                {"id": "%s", "sourceAccountId": "%s", "destinationAccountId": "%s", "amount": "150000.00",
                 "currency": "COP"}
                """.formatted(chosen, UUID.randomUUID(), UUID.randomUUID()))));

        assertProblem(response, ProblemType.MALFORMED_REQUEST, TRANSFERS);
        assertThat(response.body()).doesNotContain(chosen.toString());
        assertThat(TransferRows.countInTheTableFor(tenant)).isZero();
    }

    /** Nothing deduplicates requests, so the same body and the same key still create two holds. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("sameRequestTwice")
    void aRepeatedRequestCreatesASecondHold(String description, UnaryOperator<HttpRequest.Builder> headers)
            throws Exception {
        var tenant = UUID.randomUUID();
        var body = """
                {"sourceAccountId": "%s", "destinationAccountId": "%s", "amount": "150000.00", "currency": "COP"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        var first = send(postingJson(headers.apply(requestAs(tenant, TRANSFERS)), BodyPublishers.ofString(body)));
        var second = send(postingJson(headers.apply(requestAs(tenant, TRANSFERS)), BodyPublishers.ofString(body)));

        assertThat(List.of(first.statusCode(), second.statusCode())).containsOnly(201);
        assertThat(idOf(first)).isNotEqualTo(idOf(second));
        assertThat(TransferRows.countInTheTableFor(tenant)).isEqualTo(2);
    }

    static Stream<Arguments> sameRequestTwice() {
        var key = UUID.randomUUID().toString();
        return Stream.of(
                Arguments.of("without a key", (UnaryOperator<HttpRequest.Builder>) request -> request),
                Arguments.of("with the same key",
                        (UnaryOperator<HttpRequest.Builder>) request -> request.header("Idempotency-Key", key)));
    }

    @Test
    void anAuthenticatedResponseIsNeitherCachedNorSniffed() throws Exception {
        var response = send(postingJson(requestAs(UUID.randomUUID(), TRANSFERS), aValidBody()));

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Cache-Control"))
                .hasValueSatisfying(value -> assertThat(value).contains("no-store"));
        assertThat(response.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
    }

    private static String idOf(HttpResponse<String> response) {
        return JSON.readTree(response.body()).path("id").asString();
    }
}
