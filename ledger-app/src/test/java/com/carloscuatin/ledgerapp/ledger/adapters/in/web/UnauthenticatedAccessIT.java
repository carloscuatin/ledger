package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.carloscuatin.ledgerapp.ledger.adapters.out.persistence.LedgerDatabase;
import com.carloscuatin.ledgerapp.ledger.adapters.out.persistence.TransferRows;

import jakarta.servlet.Filter;
import tools.jackson.databind.json.JsonMapper;

/**
 * The production security chain exactly as it ships, on a real server: nothing here adds a principal, so these tests
 * see what anyone on the network would.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("api")
class UnauthenticatedAccessIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    /** Every filter of the chain, by name and in order, so a new one has to be looked at, whatever it is called. */
    static final List<String> PRODUCTION_FILTERS = List.of(
            "DisableEncodeUrlFilter",
            "WebAsyncManagerIntegrationFilter",
            "SecurityContextHolderFilter",
            "HeaderWriterFilter",
            "RequestCacheAwareFilter",
            "SecurityContextHolderAwareRequestFilter",
            "AnonymousAuthenticationFilter",
            "SessionManagementFilter",
            "ExceptionTranslationFilter",
            "AuthorizationFilter");

    @DynamicPropertySource
    static void connectAsTheApplication(DynamicPropertyRegistry registry) {
        LedgerDatabase.connectAsTheApplication(registry);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private SecurityFilterChain chain;

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
        "POST, /v1/transfers",
        "GET, /v1/transfers",
        "GET, /v1/transfers/00000000-0000-7000-8000-000000000001",
        "GET, /nope",
        "GET, /error",
        "OPTIONS, /v1/transfers",
        "POST, /transfers",
        "POST, /1/transfers",
        "POST, /v1.0/transfers",
        "POST, /v01/transfers",
        "POST, /V1/transfers",
        "POST, /vx/transfers",
        "POST, /v0/transfers",
        "POST, /v2/transfers"})
    void everyRouteAnswersUnauthenticatedProblem(String method, String path) throws Exception {
        var response = send(request(path).method(method, BodyPublishers.noBody()));

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        assertThat(response.headers().allValues("WWW-Authenticate")).containsExactly("DPoP");
        assertThat(JSON.readTree(response.body()).path("type").asString()).endsWith("/problems/unauthenticated");
    }

    /**
     * Spring Security registers these for itself, in front of any authorization rule, unless the chain turns them off:
     * a closed application must not answer them differently from any other route.
     */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
        "GET, /logout",
        "POST, /logout",
        "GET, /login",
        "POST, /login"})
    void routesTheFrameworkWouldAnswerByItselfAreClosedToo(String method, String path) throws Exception {
        var response = send(request(path).method(method, BodyPublishers.noBody()));

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Location")).isEmpty();
        assertThat(JSON.readTree(response.body()).path("type").asString()).endsWith("/problems/unauthenticated");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "Bearer made-up-token",
        "Basic dXNlcjptYWRlLXVwLXBhc3N3b3Jk"})
    void aCredentialNobodyCanVerifyIsRefusedAndStoresNothing(String credential) throws Exception {
        var rowsBefore = TransferRows.countInTheTable();

        var response = send(createATransfer("/v1/transfers").header("Authorization", credential));

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(TransferRows.countInTheTable()).isEqualTo(rowsBefore);
    }

    @Test
    void aTenantChosenByTheCallerOpensNothing() throws Exception {
        var tenant = UUID.randomUUID();
        var seeded = TransferRows.seed(tenant);
        var byParameter = "/v1/transfers?tenant_id=" + tenant;
        var rowsBefore = TransferRows.countInTheTable();

        var responses = List.of(
                send(createATransfer("/v1/transfers").header("X-Tenant-Id", tenant.toString())),
                send(createATransfer(byParameter)),
                send(request("/v1/transfers").header("X-Tenant-Id", tenant.toString())),
                send(request(byParameter)));

        assertThat(responses).allSatisfy(response -> {
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.body()).doesNotContain(seeded.toString());
        });
        assertThat(TransferRows.countInTheTable()).isEqualTo(rowsBefore);
    }

    /**
     * Starts its own processes, because the context of this class may have started before the output was being captured.
     * The count of startup banners shows that the capture saw them.
     */
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void noProcessOffersAGeneratedCredential(CapturedOutput output) {
        try (var api = LedgerDatabase.startApi("ledger_app", "test-only-app", 0)) {
            assertThat(output.getAll()).doesNotContain("generated security password");
            assertThat(api.getBeansOfType(UserDetailsService.class)).isEmpty();
            assertThat(api.getBeansOfType(AuthenticationProvider.class)).isEmpty();
            assertThat(api.getBeansOfType(AuthenticationManager.class)).isEmpty();
        }
        LedgerDatabase.startApplication(LedgerDatabase.url()).close();
        LedgerDatabase.startMigrateProfile().close();

        assertThat(output.getAll()).doesNotContain("generated security password");
        assertThat(output.getAll().split(":: Spring Boot ::", -1)).hasSize(4);
    }

    /**
     * Spring Security applies to the chain any bean of type {@code Customizer<HttpSecurity>}, and of {@code Customizer<T>}
     * for what a method of {@code HttpSecurity} configures, which is how a test adds its principal. Tests are on the
     * classpath of the process started here, so this also sees whether one of them could reach it by being scanned.
     */
    @Test
    void nothingCustomizesTheProductionChain() {
        assertThat(context.getBeanNamesForType(Customizer.class)).isEmpty();
        try (var api = LedgerDatabase.startApi("ledger_app", "test-only-app", 0)) {
            assertThat(api.getBeanNamesForType(Customizer.class)).isEmpty();
        }
    }

    /**
     * The anonymous filter is the one that remains close to authenticating, and it is what the rule of the chain refuses:
     * it marks a request as anonymous, never as authenticated.
     */
    @Test
    void theProductionChainHasNoFilterThatAuthenticates() {
        assertThat(context.getBeansOfType(SecurityFilterChain.class)).hasSize(1);
        assertThat(chain.getFilters().stream().map(Filter::getClass).map(Class::getSimpleName))
                .containsExactlyElementsOf(PRODUCTION_FILTERS);
    }

    @Test
    void aRefusalIsNeitherCachedNorSniffed() throws Exception {
        var headers = send(request("/v1/transfers")).headers();

        assertThat(headers.firstValue("Cache-Control"))
                .hasValueSatisfying(value -> assertThat(value).contains("no-store"));
        assertThat(headers.firstValue("X-Content-Type-Options")).hasValue("nosniff");
    }

    private HttpRequest.Builder createATransfer(String path) {
        var body = """
                {"sourceAccountId": "%s", "destinationAccountId": "%s", "amount": "150000.00", "currency": "COP"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());
        return request(path).header("Content-Type", "application/json").POST(BodyPublishers.ofString(body));
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:%d%s".formatted(port, path)));
    }

    private static HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return CLIENT.send(request.build(), BodyHandlers.ofString());
    }
}
