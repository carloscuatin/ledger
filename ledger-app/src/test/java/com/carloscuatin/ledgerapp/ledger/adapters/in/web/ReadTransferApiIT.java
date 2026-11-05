package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import static com.carloscuatin.ledgerapp.ledger.adapters.in.web.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.http.HttpRequest.BodyPublishers;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.carloscuatin.ledgerapp.ledger.adapters.out.persistence.TransferRows;

class ReadTransferApiIT extends AuthenticatedApi {

    private static final String TRANSFERS = "/v1/transfers/";

    @Test
    void aTenantReadsItsTransfer() throws Exception {
        var tenant = UUID.randomUUID();
        var source = UUID.randomUUID();
        var destination = UUID.randomUUID();
        var created = send(postingJson(requestAs(tenant, "/v1/transfers"), BodyPublishers.ofString("""
                {"sourceAccountId": "%s", "destinationAccountId": "%s", "amount": "0150000.5", "currency": "COP"}
                """.formatted(source, destination))));
        var id = JSON.readTree(created.body()).path("id").asString();

        var response = send(requestAs(tenant, TRANSFERS + id));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/json");
        var transfer = JSON.readTree(response.body());
        assertThat(transfer.propertyNames()).containsExactlyInAnyOrder("id", "sourceAccountId", "destinationAccountId",
                "amount", "currency", "status");
        assertThat(transfer.path("id").asString()).isEqualTo(id);
        assertThat(transfer.path("sourceAccountId").asString()).isEqualTo(source.toString());
        assertThat(transfer.path("destinationAccountId").asString()).isEqualTo(destination.toString());
        assertThat(transfer.path("amount").isString()).isTrue();
        assertThat(transfer.path("amount").asString()).isEqualTo("150000.50");
        assertThat(transfer.path("currency").asString()).isEqualTo("COP");
        assertThat(transfer.path("status").asString()).isEqualTo("PENDING");
    }

    /** A read reports the state a row is in, whichever use case wrote it. */
    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "POSTED", "VOIDED", "EXPIRED"})
    void everyStatusTheSchemaAllowsIsReported(String status) throws Exception {
        var tenant = UUID.randomUUID();
        var row = TransferRows.seedWithStatus(tenant, status);

        var response = send(requestAs(tenant, TRANSFERS + row));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(response.body()).path("status").asString()).isEqualTo(status);
    }

    /** What was stored stays readable after a deployment stops operating its currency, which only limits new ones. */
    @ParameterizedTest
    @CsvSource({"12345, COP, 123.45", "12345, USD, 123.45", "12345, JPY, 12345", "12345, KWD, 12.345"})
    void anAmountHasTheDecimalsOfItsCurrencyEvenIfItIsNotEnabled(long minorUnits, String currency, String amount)
            throws Exception {
        var tenant = UUID.randomUUID();
        var row = TransferRows.seedAsSuperuser(tenant, minorUnits, currency, "PENDING");

        var response = send(requestAs(tenant, TRANSFERS + row));

        assertThat(response.statusCode()).isEqualTo(200);
        var transfer = JSON.readTree(response.body());
        assertThat(transfer.path("amount").asString()).isEqualTo(amount);
        assertThat(transfer.path("currency").asString()).isEqualTo(currency);
    }

    /**
     * The schema checks the shape of a code, and the application refuses these two before storing anything, so such a row
     * came from outside it. Without a scale there is no amount to report, and guessing one would misreport money.
     */
    @ParameterizedTest
    @ValueSource(strings = {"ZZZ", "XAU"})
    void aRowWithoutAnAmountToShowIsAFailureThatRevealsNothing(String currency) throws Exception {
        var tenant = UUID.randomUUID();
        var row = TransferRows.seedAsSuperuser(tenant, 12345, currency, "PENDING");
        var path = TRANSFERS + row;

        var response = send(requestAs(tenant, path));

        assertProblem(response, ProblemType.INTERNAL_ERROR, path);
        assertThat(response.body()).doesNotContain(currency).doesNotContain("12345");
    }

    @Test
    void anUnknownTransferIsNotFound() throws Exception {
        var path = TRANSFERS + UUID.randomUUID();

        assertProblem(send(requestAs(UUID.randomUUID(), path)), ProblemType.NOT_FOUND, path);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void anUnknownTransferIsNotAnErrorInTheLog(CapturedOutput output) throws Exception {
        var id = UUID.randomUUID();

        assertThat(send(requestAs(UUID.randomUUID(), TRANSFERS + id)).statusCode()).isEqualTo(404);

        assertThat(output).doesNotContain(" ERROR ").doesNotContain(" WARN ").doesNotContain("Exception")
                .doesNotContain(id.toString());
    }

    @Test
    void aTransferOfAnotherTenantIsNotFoundAndTheRowIsThere() throws Exception {
        var owner = UUID.randomUUID();
        var row = TransferRows.seed(owner);
        var path = TRANSFERS + row;

        var response = send(requestAs(UUID.randomUUID(), path));

        assertProblem(response, ProblemType.NOT_FOUND, path);
        assertThat(response.body()).doesNotContain(owner.toString());
        assertThat(TransferRows.tenantOf(row)).isEqualTo(owner);
    }

    /**
     * Read off the socket, so this compares what a client really receives, in order: the status line, every header with
     * its spelling, and the body. The path is expected to differ where it is repeated, and the date because time passes.
     */
    @Test
    void aTransferOfAnotherTenantAnswersByteForByteLikeOneThatDoesNotExist() throws Exception {
        var asker = UUID.randomUUID();
        var ofAnother = TransferRows.seed(UUID.randomUUID());
        var unknown = UUID.randomUUID();

        var forAnother = rawAnswerTo(asker, "GET", ofAnother);
        var forUnknown = rawAnswerTo(asker, "GET", unknown);

        assertThat(forAnother).startsWith("HTTP/1.1 404 ").contains(ofAnother.toString());
        assertThat(comparable(forAnother, ofAnother)).isEqualTo(comparable(forUnknown, unknown));
        assertThat(comparable(rawAnswerTo(asker, "HEAD", ofAnother), ofAnother))
                .isEqualTo(comparable(rawAnswerTo(asker, "HEAD", unknown), unknown));
    }

    @Test
    void aTenantInTheRequestDoesNotOpenAnotherTenantsTransfer() throws Exception {
        var owner = UUID.randomUUID();
        var row = TransferRows.seed(owner);
        var path = TRANSFERS + row;

        var response = send(requestAs(UUID.randomUUID(), path + "?tenant_id=" + owner)
                .header("X-Tenant-Id", owner.toString()));

        assertProblem(response, ProblemType.NOT_FOUND, path);
    }

    @Test
    void aMalformedIdentifierIsRefusedWithoutRepeatingItInTheDetail() throws Exception {
        var path = TRANSFERS + "not-a-uuid";

        var response = send(requestAs(UUID.randomUUID(), path));

        var problem = assertProblem(response, ProblemType.MALFORMED_REQUEST, path);
        assertThat(problem.path("detail").asString()).doesNotContain("not-a-uuid");
    }

    private String rawAnswerTo(UUID tenant, String method, UUID id) throws IOException {
        return overASocket("""
                %s %s%s HTTP/1.1\r
                Host: ledger\r
                %s: %s\r
                Connection: close\r
                \r
                """.formatted(method, TRANSFERS, id, TestAuthentication.PRINCIPAL_HEADER, tenant));
    }

    private static String comparable(String answer, UUID id) {
        return answer.replace(id.toString(), "{id}").replaceFirst("(?m)^Date: .*$", "Date: {date}");
    }
}
