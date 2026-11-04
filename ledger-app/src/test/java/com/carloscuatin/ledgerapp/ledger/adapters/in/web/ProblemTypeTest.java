package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatusCode;

import com.carloscuatin.ledger.contract.v1.model.Problem;

class ProblemTypeTest {

    private static final String BASE = "https://ledger.carloscuatin.com/problems/";

    /** The enum of the contract is generated from it, so this fails when the catalog and the contract part ways. */
    @Test
    void theCatalogIsExactlyTheOneInTheContract() {
        var catalog = Arrays.stream(ProblemType.values()).map(type -> type.type().toString());
        var contract = Arrays.stream(Problem.TypeEnum.values()).map(Problem.TypeEnum::getValue).toList();

        assertThat(catalog).containsExactlyInAnyOrderElementsOf(contract);
    }

    /** Clients program against these, so changing one is a change of the API and not a detail of this class. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            UNAUTHENTICATED        | unauthenticated        | 401
            TENANT_REQUIRED        | tenant-required        | 403
            MALFORMED_REQUEST      | malformed-request      | 400
            VALIDATION_FAILED      | validation-failed      | 400
            INVALID_CURSOR         | invalid-cursor         | 400
            UNSUPPORTED_VERSION    | unsupported-version    | 400
            BAD_REQUEST            | bad-request            | 400
            NOT_FOUND              | not-found              | 404
            METHOD_NOT_ALLOWED     | method-not-allowed     | 405
            NOT_ACCEPTABLE         | not-acceptable         | 406
            UNSUPPORTED_MEDIA_TYPE | unsupported-media-type | 415
            INVALID_AMOUNT         | invalid-amount         | 422
            CURRENCY_NOT_ENABLED   | currency-not-enabled   | 422
            SAME_ACCOUNT           | same-account           | 422
            INTERNAL_ERROR         | internal-error         | 500
            """)
    void eachTypeHasItsStatus(ProblemType type, String slug, int status) {
        var problem = type.problemAt("/v1/transfers");

        assertThat(problem.getType()).hasToString(BASE + slug);
        assertThat(problem.getStatus()).isEqualTo(status);
        assertThat(problem.getTitle()).isEqualTo(type.title()).isNotBlank();
        assertThat(problem.getDetail()).isEqualTo(type.detail()).isNotBlank();
        assertThat(problem.getInstance()).hasToString("/v1/transfers");
    }

    @ParameterizedTest
    @CsvSource({
        "400, BAD_REQUEST",
        "404, NOT_FOUND",
        "405, METHOD_NOT_ALLOWED",
        "406, NOT_ACCEPTABLE",
        "415, UNSUPPORTED_MEDIA_TYPE",
        "414, BAD_REQUEST",
        "431, BAD_REQUEST",
        "500, INTERNAL_ERROR",
        "503, INTERNAL_ERROR"})
    void aStatusAloneDecidesItsType(int status, ProblemType type) {
        assertThat(ProblemType.forStatus(HttpStatusCode.valueOf(status))).isEqualTo(type);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/v1/transfers", "/v1/transfers/00000000-0000-7000-8000-000000000001", "/a.b_c~d-e"})
    void aPlainPathIsTheInstance(String path) {
        assertThat(ProblemType.NOT_FOUND.problemAt(path).getInstance()).hasToString(path);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/v1/transfers;x=1",
        "/v1/transfers%0a",
        "/v1/transfers%0d%0aInjected:%20header",
        "/v1/%22quoted%22",
        "/v1/<script>",
        "/v1/transfers\n",
        "/v1/café",
        "relative",
        ""})
    void anyOtherPathIsNotRepeated(String path) {
        assertThat(ProblemType.NOT_FOUND.problemAt(path).getInstance()).hasToString("/");
    }

    @Test
    void theInstanceIsBounded() {
        var longest = "/" + "a".repeat(255);
        var longer = "/" + "a".repeat(256);

        assertThat(ProblemType.NOT_FOUND.problemAt(longest).getInstance()).hasToString(longest);
        assertThat(ProblemType.NOT_FOUND.problemAt(longer).getInstance()).hasToString("/");
    }
}
