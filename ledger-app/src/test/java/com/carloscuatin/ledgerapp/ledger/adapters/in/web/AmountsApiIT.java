package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import static com.carloscuatin.ledgerapp.ledger.adapters.in.web.ProblemAssertions.assertProblem;
import static com.carloscuatin.ledgerapp.ledger.adapters.in.web.ProblemAssertions.assertRejectedInputs;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.carloscuatin.ledgerapp.ledger.adapters.out.persistence.TransferRows;

import tools.jackson.databind.JsonNode;

class AmountsApiIT extends AuthenticatedApi {

    private static final String TRANSFERS = "/v1/transfers";
    private static final String MARK = "SYNTHETICMARK";

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"150000.5", "150000", "true"})
    void aJsonValueThatIsNotAStringIsNotAnAmount(String literal) throws Exception {
        var tenant = UUID.randomUUID();

        var response = create(tenant, literal, "\"COP\"");

        assertRefusedWithoutARow(response, ProblemType.MALFORMED_REQUEST, tenant);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"978", "true"})
    void aJsonValueThatIsNotAStringIsNotACurrency(String literal) throws Exception {
        var tenant = UUID.randomUUID();

        var response = create(tenant, "\"150000.00\"", literal);

        assertRefusedWithoutARow(response, ProblemType.MALFORMED_REQUEST, tenant);
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"abc", "1e5", "-10.00", "+10", "10.", ".5", "١٠", "", "10\\n"})
    void aBadlyFormedAmountIsRefused(String amount) throws Exception {
        var tenant = UUID.randomUUID();

        var response = create(tenant, "\"" + amount + "\"", "\"COP\"");

        var problem = assertRefusedWithoutARow(response, ProblemType.VALIDATION_FAILED, tenant);
        assertRejectedInputs(problem, List.of(Map.of("pointer", "/amount", "reason", "invalid")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.005", "92233720368547758.08"})
    void anAmountBeyondTheScaleOrTheRangeIsRefused(String amount) throws Exception {
        var tenant = UUID.randomUUID();

        var response = create(tenant, "\"" + amount + "\"", "\"COP\"");

        assertRefusedWithoutARow(response, ProblemType.INVALID_AMOUNT, tenant);
        assertThat(response.body()).doesNotContain(amount);
    }

    @Test
    void aZeroAmountIsRefused() throws Exception {
        var tenant = UUID.randomUUID();

        var response = create(tenant, "\"0.00\"", "\"COP\"");

        assertRefusedWithoutARow(response, ProblemType.INVALID_AMOUNT, tenant);
    }

    @ParameterizedTest
    @ValueSource(strings = {"USD", "ZZZ", "XAU"})
    void aCurrencyThatDoesNotOperateIsRefused(String currency) throws Exception {
        var tenant = UUID.randomUUID();

        var response = create(tenant, "\"150000.00\"", "\"" + currency + "\"");

        assertRefusedWithoutARow(response, ProblemType.CURRENCY_NOT_ENABLED, tenant);
        assertThat(response.body()).doesNotContain(currency);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cop", "CO", "COPP", "COP\\n"})
    void aBadlyFormedCurrencyIsRefused(String currency) throws Exception {
        var tenant = UUID.randomUUID();

        var response = create(tenant, "\"150000.00\"", "\"" + currency + "\"");

        var problem = assertRefusedWithoutARow(response, ProblemType.VALIDATION_FAILED, tenant);
        assertRejectedInputs(problem, List.of(Map.of("pointer", "/currency", "reason", "invalid")));
    }

    @Test
    void theSameAccountOnBothSidesIsRefused() throws Exception {
        var tenant = UUID.randomUUID();
        var account = UUID.randomUUID();

        var response = send(postingJson(requestAs(tenant, TRANSFERS), BodyPublishers.ofString("""
                {"sourceAccountId": "%s", "destinationAccountId": "%s", "amount": "150000.00", "currency": "COP"}
                """.formatted(account, account))));

        assertRefusedWithoutARow(response, ProblemType.SAME_ACCOUNT, tenant);
        assertThat(response.body()).doesNotContain(account.toString());
    }

    /**
     * Each of these is the client's to fix. As an uncaught exception, one would reach the server, which logs an error with
     * its trace, and an error nobody on this side can act on hides the ones somebody should.
     */
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void anExpectedRefusalIsNotAnErrorInTheLog(CapturedOutput output) throws Exception {
        var account = UUID.randomUUID();
        var tenant = UUID.randomUUID();

        var responses = List.of(
                create(tenant, "\"150000.00\"", "\"XAU\""),
                create(tenant, "\"150000.00\"", "\"ZZZ\""),
                create(tenant, "\"150000.00\"", "\"USD\""),
                create(tenant, "\"10.005\"", "\"COP\""),
                create(tenant, "\"0.00\"", "\"COP\""),
                send(postingJson(requestAs(tenant, TRANSFERS),
                        BodyPublishers.ofString(
                                """
                                        {"sourceAccountId": "%s", "destinationAccountId": "%s", "amount": "150000.00", "currency": "COP"}
                                        """
                                        .formatted(account, account)))));

        assertThat(responses).allSatisfy(response -> assertThat(response.statusCode()).isEqualTo(422));
        assertThat(output).doesNotContain(" ERROR ").doesNotContain(" WARN ").doesNotContain("Exception");
    }

    @Test
    void anUnknownFieldIsRefused() throws Exception {
        var tenant = UUID.randomUUID();

        var response = send(postingJson(requestAs(tenant, TRANSFERS), BodyPublishers.ofString("""
                {"sourceAccountId": "%s", "destinationAccountId": "%s", "amount": "150000.00", "currency": "COP",
                 "note": "unexpected"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID()))));

        assertRefusedWithoutARow(response, ProblemType.MALFORMED_REQUEST, tenant);
    }

    @ParameterizedTest
    @CsvSource({"0150000.5, 150000.50", "150000.50, 150000.50", "7, 7.00"})
    void theAmountComesBackInItsCanonicalForm(String sent, String expected) throws Exception {
        var response = create(UUID.randomUUID(), "\"" + sent + "\"", "\"COP\"");

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(JSON.readTree(response.body()).path("amount").asString()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(ints = {33, 10_000})
    void anOversizedAmountIsRefusedWithoutRepeatingIt(int length) throws Exception {
        var tenant = UUID.randomUUID();

        var response = create(tenant, "\"" + markedAmount(length) + "\"", "\"COP\"");

        var problem = assertRefusedWithoutARow(response, ProblemType.VALIDATION_FAILED, tenant);
        assertRejectedInputs(problem, List.of(Map.of("pointer", "/amount", "reason", "invalid")));
        assertThat(response.body()).doesNotContain(MARK);
    }

    @Test
    void anAmountThatMakesTheBodyTooLongIsMalformedWithoutRepeatingIt() throws Exception {
        var tenant = UUID.randomUUID();

        var response = create(tenant, "\"" + markedAmount(100_000) + "\"", "\"COP\"");

        assertRefusedWithoutARow(response, ProblemType.MALFORMED_REQUEST, tenant);
        assertThat(response.body()).doesNotContain(MARK);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void aBodyOverTheLimitStaysOutOfTheLog(CapturedOutput output) throws Exception {
        var response = create(UUID.randomUUID(), "\"" + markedAmount(100_000) + "\"", "\"COP\"");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(output).doesNotContain(MARK);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void aBodyOverTheLimitStaysOutOfADebugLog(CapturedOutput output) throws Exception {
        var response = atDebugLevel(() -> create(UUID.randomUUID(), "\"" + markedAmount(100_000) + "\"", "\"COP\""));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(output).contains(" DEBUG ").doesNotContain(MARK);
    }

    @ParameterizedTest
    @ValueSource(ints = {33, 10_000})
    @ExtendWith(OutputCaptureExtension.class)
    void anOversizedAmountStaysOutOfTheLog(int length, CapturedOutput output) throws Exception {
        var response = create(UUID.randomUUID(), "\"" + markedAmount(length) + "\"", "\"COP\"");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(output).doesNotContain(MARK);
    }

    @ParameterizedTest
    @ValueSource(ints = {33, 10_000})
    @ExtendWith(OutputCaptureExtension.class)
    void anOversizedAmountStaysOutOfADebugLog(int length, CapturedOutput output) throws Exception {
        var response = atDebugLevel(() -> create(UUID.randomUUID(), "\"" + markedAmount(length) + "\"", "\"COP\""));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(output).contains(" DEBUG ").doesNotContain(MARK);
    }

    /**
     * The parser quotes what it could not read in its message, and the framework prints that message before resolving the
     * exception. The framework's own DEBUG lines print the path and the query as well, so only the body can be asked of it.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "{\"SYNTHETICMARK\": 1}",
        "{\"sourceAccountId\": \"SYNTHETICMARK\"}",
        "{\"amount\": SYNTHETICMARK}"})
    @ExtendWith(OutputCaptureExtension.class)
    void aBodyThatCannotBeReadStaysOutOfADebugLog(String body, CapturedOutput output) throws Exception {
        var response = atDebugLevel(
                () -> send(postingJson(requestAs(UUID.randomUUID(), TRANSFERS), BodyPublishers.ofString(body))));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(output).contains(" DEBUG ").doesNotContain(MARK);
    }

    @ParameterizedTest(name = "{0} bytes, {1}")
    @CsvSource({"200, false", "200, true", "16384, false", "16384, true"})
    void aBodyUpToTheLimitIsRead(int size, boolean chunked) throws Exception {
        var tenant = UUID.randomUUID();

        var response = send(postingJson(requestAs(tenant, TRANSFERS), publisher(paddedBody(size), chunked)));

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(TransferRows.countInTheTableFor(tenant)).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0} bytes, {1}")
    @CsvSource({"16385, false", "16385, true", "17408, false", "17408, true", "100000, false", "100000, true",
        "1000000, false", "1000000, true"})
    void aBodyOverTheLimitIsRefused(int size, boolean chunked) throws Exception {
        var tenant = UUID.randomUUID();

        var response = send(postingJson(requestAs(tenant, TRANSFERS), publisher(paddedBody(size), chunked)));

        assertRefusedWithoutARow(response, ProblemType.MALFORMED_REQUEST, tenant);
    }

    /** However the body is encoded, the limit must hold: another charset can reach the parser by another path. */
    @ParameterizedTest
    @ValueSource(strings = {"UTF-8", "ISO-8859-1", "UTF-16"})
    void theLimitHoldsInEveryCharset(String charsetName) throws Exception {
        var tenant = UUID.randomUUID();
        var charset = Charset.forName(charsetName);
        var body = BodyPublishers.ofString(new String(paddedBody(100_000), StandardCharsets.UTF_8), charset);

        var response = send(requestAs(tenant, TRANSFERS)
                .header("Content-Type", "application/json;charset=" + charsetName).POST(body));

        assertRefusedWithoutARow(response, ProblemType.MALFORMED_REQUEST, tenant);
    }

    private static String markedAmount(int length) {
        return MARK + "7".repeat(length - MARK.length());
    }

    /** Valid JSON of an exact size: only its length can be the reason for a refusal. */
    private static byte[] paddedBody(int size) {
        var start = """
                {"sourceAccountId": "%s", "destinationAccountId": "%s", "amount": "150000.00", "currency": "COP"
                """.formatted(UUID.randomUUID(), UUID.randomUUID());
        return (start + " ".repeat(size - start.length() - 1) + "}").getBytes(StandardCharsets.UTF_8);
    }

    private static HttpRequest.BodyPublisher publisher(byte[] body, boolean chunked) {
        return chunked
                ? BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body))
                : BodyPublishers.ofByteArray(body);
    }

    private HttpResponse<String> atDebugLevel(Callable<HttpResponse<String>> call) throws Exception {
        var logging = LoggingSystem.get(getClass().getClassLoader());
        logging.setLogLevel("org.springframework.web", LogLevel.DEBUG);
        try {
            return call.call();
        } finally {
            logging.setLogLevel("org.springframework.web", null);
        }
    }

    private HttpResponse<String> create(UUID tenant, String amountLiteral, String currencyLiteral) throws Exception {
        var body = """
                {"sourceAccountId": "%s", "destinationAccountId": "%s", "amount": %s, "currency": %s}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), amountLiteral, currencyLiteral);
        return send(postingJson(requestAs(tenant, TRANSFERS), BodyPublishers.ofString(body)));
    }

    private static JsonNode assertRefusedWithoutARow(HttpResponse<String> response, ProblemType type, UUID tenant)
            throws Exception {
        var problem = assertProblem(response, type, TRANSFERS);
        assertThat(TransferRows.countInTheTableFor(tenant)).isZero();
        return problem;
    }
}
