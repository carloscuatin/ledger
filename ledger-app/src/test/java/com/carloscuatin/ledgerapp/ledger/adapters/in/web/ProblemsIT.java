package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import static com.carloscuatin.ledgerapp.ledger.adapters.in.web.ProblemAssertions.assertNeitherCachedNorSniffed;
import static com.carloscuatin.ledgerapp.ledger.adapters.in.web.ProblemAssertions.assertProblem;
import static com.carloscuatin.ledgerapp.ledger.adapters.in.web.ProblemAssertions.assertRejectedInputs;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpRequest.BodyPublishers;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.carloscuatin.ledgerapp.ledger.adapters.out.persistence.TransferRows;

/** Each refusal of the API with its exact type, for a client that is authenticated and got as far as it can. */
class ProblemsIT extends AuthenticatedApi {

    private static final String TRANSFERS = "/v1/transfers";
    private static final String MARK = "SYNTHETICMARK";

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {
        "{oops",
        "",
        "{\"sourceAccountId\": \"SYNTHETICMARK\", \"destinationAccountId\": \"00000000-0000-7000-8000-000000000001\", "
                + "\"amount\": \"150000.00\", \"currency\": \"COP\"}"})
    void malformedJsonIsRefused(String body) throws Exception {
        var tenant = UUID.randomUUID();

        var response = send(postingJson(requestAs(tenant, TRANSFERS), BodyPublishers.ofString(body)));

        assertProblem(response, ProblemType.MALFORMED_REQUEST, TRANSFERS);
        assertThat(response.body()).doesNotContain(MARK);
        assertThat(TransferRows.countInTheTableFor(tenant)).isZero();
    }

    @Test
    void anUnknownFieldIsRefused() throws Exception {
        var tenant = UUID.randomUUID();

        var response = send(postingJson(requestAs(tenant, TRANSFERS), BodyPublishers.ofString("""
                {"sourceAccountId": "%s", "destinationAccountId": "%s", "amount": "150000.00", "currency": "COP",
                 "SYNTHETICMARK": "unexpected"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID()))));

        assertProblem(response, ProblemType.MALFORMED_REQUEST, TRANSFERS);
        assertThat(response.body()).doesNotContain(MARK);
        assertThat(TransferRows.countInTheTableFor(tenant)).isZero();
    }

    @Test
    void everyMissingFieldIsNamed() throws Exception {
        var response = send(postingJson(requestAs(UUID.randomUUID(), TRANSFERS), BodyPublishers.ofString("{}")));

        var problem = assertProblem(response, ProblemType.VALIDATION_FAILED, TRANSFERS);
        assertRejectedInputs(problem, List.of(
                Map.of("pointer", "/amount", "reason", "required"),
                Map.of("pointer", "/currency", "reason", "required"),
                Map.of("pointer", "/destinationAccountId", "reason", "required"),
                Map.of("pointer", "/sourceAccountId", "reason", "required")));
    }

    @Test
    void aNullFieldIsNamed() throws Exception {
        var response = send(postingJson(requestAs(UUID.randomUUID(), TRANSFERS), BodyPublishers.ofString("""
                {"sourceAccountId": "%s", "destinationAccountId": "%s", "amount": "150000.00", "currency": null}
                """.formatted(UUID.randomUUID(), UUID.randomUUID()))));

        var problem = assertProblem(response, ProblemType.VALIDATION_FAILED, TRANSFERS);
        assertRejectedInputs(problem, List.of(Map.of("pointer", "/currency", "reason", "required")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/plain", "application/SYNTHETICMARK"})
    void aWrongContentTypeIsRefused(String contentType) throws Exception {
        var tenant = UUID.randomUUID();

        var response = send(requestAs(tenant, TRANSFERS).header("Content-Type", contentType).POST(aValidBody()));

        assertProblem(response, ProblemType.UNSUPPORTED_MEDIA_TYPE, TRANSFERS);
        assertThat(response.body()).doesNotContainIgnoringCase(MARK).doesNotContain(contentType);
        assertThat(TransferRows.countInTheTableFor(tenant)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/xml", "application/SYNTHETICMARK"})
    void anUnacceptableResponseTypeIsRefused(String accept) throws Exception {
        var tenant = UUID.randomUUID();

        var response = send(postingJson(requestAs(tenant, TRANSFERS), aValidBody()).header("Accept", accept));

        assertProblem(response, ProblemType.NOT_ACCEPTABLE, TRANSFERS);
        assertThat(response.body()).doesNotContainIgnoringCase(MARK).doesNotContain(accept)
                .doesNotContain("application/json");
        assertThat(response.headers().firstValue("Accept")).isEmpty();
        assertThat(TransferRows.countInTheTableFor(tenant)).isZero();
    }

    @Test
    void anUnsupportedMethodIsRefused() throws Exception {
        var response = send(requestAs(UUID.randomUUID(), TRANSFERS).PUT(BodyPublishers.noBody()));

        assertProblem(response, ProblemType.METHOD_NOT_ALLOWED, TRANSFERS);
        assertThat(response.headers().firstValue("Allow"))
                .hasValueSatisfying(allowed -> assertThat(allowed).contains("GET", "POST"));
    }

    /** The server refuses this method by itself, ahead of the framework, and sends the client to the error page. */
    @Test
    void aMethodTheServerRefusesKeepsItsTypeAndHeaders() throws Exception {
        var response = send(requestAs(UUID.randomUUID(), TRANSFERS).method("TRACE", BodyPublishers.noBody()));

        assertProblem(response, ProblemType.METHOD_NOT_ALLOWED, TRANSFERS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v1/SYNTHETICMARK", "/SYNTHETICMARK"})
    void anUnknownRouteDoesNotDescribeItself(String path) throws Exception {
        var response = send(requestAs(UUID.randomUUID(), path));

        var problem = assertProblem(response, ProblemType.NOT_FOUND, path);
        assertThat(problem.path("detail").asString()).doesNotContain(MARK).doesNotContainIgnoringCase("static");
    }

    /** {@link UnauthenticatedAccessIT} sees the same without a principal. */
    @Test
    void aRejectedUrlStillGetsAProblem() throws Exception {
        var response = send(requestAs(UUID.randomUUID(), TRANSFERS + ";x=1"));

        assertProblem(response, ProblemType.BAD_REQUEST, "/");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/v1/transfers%0aSYNTHETICMARK",
        "/v1/transfers%0d%0aSYNTHETICMARK:%20injected",
        "/v1/%2e%2e/SYNTHETICMARK",
        "/v1//SYNTHETICMARK",
        "/v1/%25SYNTHETICMARK"})
    void aPathTheFirewallRejectsIsNotRepeated(String path) throws Exception {
        var response = send(requestAs(UUID.randomUUID(), path));

        assertProblem(response, ProblemType.BAD_REQUEST, "/");
        assertThat(response.body()).doesNotContain(MARK);
    }

    @ParameterizedTest
    @ValueSource(ints = {257, 7000})
    void aVeryLongPathIsNotRepeated(int length) throws Exception {
        var response = send(requestAs(UUID.randomUUID(), "/" + "a".repeat(length - 1)));

        assertProblem(response, ProblemType.NOT_FOUND, "/");
        assertThat(response.body()).doesNotContain("aaaa");
    }

    @Test
    void theErrorPageAskedForDirectlyIsNotFound() throws Exception {
        var response = send(requestAs(UUID.randomUUID(), "/error"));

        assertProblem(response, ProblemType.NOT_FOUND, "/error");
    }

    @Test
    void aMalformedIdentifierIsRefused() throws Exception {
        var path = TRANSFERS + "/SYNTHETICMARK";

        var response = send(requestAs(UUID.randomUUID(), path));

        var problem = assertProblem(response, ProblemType.MALFORMED_REQUEST, path);
        assertThat(problem.path("detail").asString()).doesNotContain(MARK);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"limit=0", "limit=101"})
    void aPageSizeOutsideItsLimitsIsNamed(String query) throws Exception {
        var response = send(requestAs(UUID.randomUUID(), TRANSFERS + "?" + query));

        var problem = assertProblem(response, ProblemType.VALIDATION_FAILED, TRANSFERS);
        assertRejectedInputs(problem, List.of(Map.of("parameter", "limit", "reason", "invalid")));
    }

    @Test
    void aCursorOverItsLengthIsNamedWithoutItsValue() throws Exception {
        var cursor = MARK + "a".repeat(65 - MARK.length());

        var response = send(requestAs(UUID.randomUUID(), TRANSFERS + "?cursor=" + cursor));

        var problem = assertProblem(response, ProblemType.VALIDATION_FAILED, TRANSFERS);
        assertRejectedInputs(problem, List.of(Map.of("parameter", "cursor", "reason", "invalid")));
        assertThat(response.body()).doesNotContain(MARK);
    }

    @Test
    void aPageSizeThatIsNotANumberIsMalformed() throws Exception {
        var response = send(requestAs(UUID.randomUUID(), TRANSFERS + "?limit=SYNTHETICMARK"));

        assertProblem(response, ProblemType.MALFORMED_REQUEST, TRANSFERS);
        assertThat(response.body()).doesNotContain(MARK);
    }

    /** What a client refused in these ways sent stays out of the log too, at the level the application ships with. */
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void aRefusedRequestLeavesItsInputOutOfTheLog(CapturedOutput output) throws Exception {
        var tenant = UUID.randomUUID();
        var unknownField = BodyPublishers.ofString("{\"SYNTHETICMARK\": 1}");

        var responses = List.of(
                send(requestAs(tenant, "/v1/SYNTHETICMARK")),
                send(requestAs(tenant, TRANSFERS + "/SYNTHETICMARK")),
                send(requestAs(tenant, TRANSFERS + "?limit=SYNTHETICMARK")),
                send(postingJson(requestAs(tenant, TRANSFERS), unknownField)),
                send(requestAs(tenant, TRANSFERS).header("Content-Type", "application/SYNTHETICMARK")
                        .POST(aValidBody())),
                send(postingJson(requestAs(tenant, TRANSFERS), aValidBody())
                        .header("Accept", "application/SYNTHETICMARK")),
                send(requestAs(tenant, TRANSFERS).PUT(BodyPublishers.noBody())),
                send(requestAs(tenant, TRANSFERS).method("SYNTHETICMARK", BodyPublishers.noBody())),
                send(requestAs(tenant, "/v2/SYNTHETICMARK")),
                send(requestAs(tenant, TRANSFERS + ";SYNTHETICMARK=1")));

        assertThat(responses).allSatisfy(response -> {
            assertThat(response.statusCode()).isBetween(400, 499);
            assertNeitherCachedNorSniffed(response.headers());
        });
        assertThat(output).doesNotContainIgnoringCase(MARK).doesNotContain(" ERROR ").doesNotContain(" WARN ");
    }
}
