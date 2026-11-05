package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import static com.carloscuatin.ledgerapp.ledger.adapters.in.web.ProblemAssertions.assertNeitherCachedNorSniffed;
import static com.carloscuatin.ledgerapp.ledger.adapters.in.web.ProblemAssertions.assertProblem;
import static com.carloscuatin.ledgerapp.ledger.adapters.in.web.ProblemAssertions.assertProblemOnTheWire;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.apache.catalina.Context;
import org.apache.tomcat.util.descriptor.web.FilterMap;
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
import org.springframework.boot.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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

    /**
     * Every filter of the server, in the order it runs them. What stands ahead of the security chain sees a request
     * nobody has authenticated, so a new one has to be looked at: reading a body or a parameter there costs the server
     * memory on behalf of anyone on the network.
     */
    static final List<String> SERVER_FILTERS = List.of(
            "pathParameterFilter",
            "characterEncodingFilter",
            "requestContextFilter",
            "springSecurityFilterChain",
            "Tomcat WebSocket (JSR356) Filter");

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

        assertProblem(response, ProblemType.UNAUTHENTICATED, path);
        assertThat(response.headers().allValues("WWW-Authenticate")).containsExactly("DPoP");
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
     * The other way to put something in front of a controller, and how Spring adds an open session: every configurer of
     * Spring MVC by name, in the process started here too, so a new one has to be looked at.
     */
    @Test
    void onlyTheExpectedConfigurersShapeSpringMvcInProduction() {
        var expected = new String[] {
            "webConfiguration",
            "org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration$WebMvcAutoConfigurationAdapter",
            "org.springframework.data.web.config.SpringDataWebConfiguration",
            "org.springframework.security.config.annotation.web.configuration.WebMvcSecurityConfiguration"};

        assertThat(context.getBeanNamesForType(WebMvcConfigurer.class)).containsExactlyInAnyOrder(expected);
        try (var api = LedgerDatabase.startApi("ledger_app", "test-only-app", 0)) {
            assertThat(api.getBeanNamesForType(WebMvcConfigurer.class)).containsExactlyInAnyOrder(expected);
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
    void theServerRunsOnlyTheKnownFilters() {
        var server = (TomcatWebServer) ((WebServerApplicationContext) context).getWebServer();
        var application = (Context) server.getTomcat().getHost().findChildren()[0];

        assertThat(Arrays.stream(application.findFilterMaps()).map(FilterMap::getFilterName))
                .containsExactlyElementsOf(SERVER_FILTERS);
    }

    /**
     * The headers of a request whose body never comes. The refusal can only arrive if nothing ahead of the chain waits
     * for that body; a server that reads it first waits until the socket's limit ends the test. No clock decides the
     * result, because the body does not exist, however slow the machine is.
     */
    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void aFormBodyIsNotReadToRefuseARequest(String method) throws Exception {
        var answer = TestHttp.problemWithoutSendingTheBody(port, method
                + " /v1/transfers HTTP/1.1\r\nHost: localhost\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: 1000\r\nConnection: close\r\n\r\n");

        assertProblemOnTheWire(answer, ProblemType.UNAUTHENTICATED, "/v1/transfers");
    }

    /** A body that cannot be decoded is the client's problem only if something reads it, and nothing should. */
    @ParameterizedTest
    @ValueSource(strings = {"a=%ZZ", "a=%", "a=%FF"})
    @ExtendWith(OutputCaptureExtension.class)
    void aBrokenFormBodyIsNeverDecoded(String body, CapturedOutput output) throws Exception {
        var answer = overASocket("PUT /nope HTTP/1.1\r\nHost: localhost\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: " + body.length()
                + "\r\nConnection: close\r\n\r\n" + body);

        assertProblemOnTheWire(answer, ProblemType.UNAUTHENTICATED, "/nope");
        assertThat(output).doesNotContain(" ERROR ").doesNotContain(" WARN ");
    }

    /**
     * A path parameter is the one part of a path the server does not decode, so a broken escape in it gets past the
     * server and fails in the first thing that does decode it, ahead of the firewall. The same refusal the firewall gives
     * any other path parameter, and nothing in the log, or a very short request would cost the server far more log than it
     * cost the client to send.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "GET /v1/transfers;a=%ZZ",
        "POST /v1/transfers;a=%",
        "GET /nope;a=%ZZ",
        "GET /v1;a=%ZZ/transfers",
        "GET /;a=%FF",
        "GET /v1/transfers;a=%ZZSYNTHETICMARK%0d%0aSYNTHETICMARK"})
    @ExtendWith(OutputCaptureExtension.class)
    void aPathParameterThatCannotBeDecodedIsRefusedQuietly(String requestLine, CapturedOutput output) throws Exception {
        var answer = overASocket(requestLine + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");

        assertProblemOnTheWire(answer, ProblemType.BAD_REQUEST, "/");
        assertThat(answer).doesNotContain("SYNTHETICMARK");
        assertThat(output).doesNotContain("SYNTHETICMARK").doesNotContain(" ERROR ").doesNotContain(" WARN ")
                .doesNotContain("\tat ");
    }

    /** The server refuses this method itself and forwards to the error page, which then sees the same broken path. */
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void theErrorPageDoesNotDependOnThePathOfTheRequest(CapturedOutput output) throws Exception {
        var answer = overASocket(
                "TRACE /v1/transfers;a=%ZZSYNTHETICMARK HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");

        assertProblemOnTheWire(answer, ProblemType.METHOD_NOT_ALLOWED, "/");
        assertThat(answer).doesNotContain("SYNTHETICMARK");
        assertThat(output).doesNotContain("SYNTHETICMARK").doesNotContain(" ERROR ").doesNotContain(" WARN ")
                .doesNotContain("\tat ");
    }

    @Test
    void aRefusalIsNeitherCachedNorSniffed() throws Exception {
        assertNeitherCachedNorSniffed(send(request("/v1/transfers")).headers());
    }

    /** Refused ahead of the rules of the chain, by the filter or by its firewall, so the same with a principal or without. */
    @ParameterizedTest
    @ValueSource(strings = {"/v1/transfers;x=1", "/v1/transfers%0a"})
    void aRejectedUrlStillGetsAProblem(String path) throws Exception {
        var response = send(request(path));

        assertProblem(response, ProblemType.BAD_REQUEST, "/");
    }

    @Test
    void aMethodTheServerRefusesStillGetsAProblem() throws Exception {
        var response = send(request("/v1/transfers").method("TRACE", BodyPublishers.noBody()));

        assertProblem(response, ProblemType.METHOD_NOT_ALLOWED, "/v1/transfers");
    }

    @ParameterizedTest
    @ValueSource(ints = {257, 7000})
    void aVeryLongPathIsNotRepeated(int length) throws Exception {
        var response = send(request("/" + "a".repeat(length - 1)));

        assertProblem(response, ProblemType.UNAUTHENTICATED, "/");
    }

    /** The server answers these itself, without reaching the application, so all that can be asked is that it says little. */
    @ParameterizedTest
    @ValueSource(strings = {"BAD", "GET /v1%2Ftransfers HTTP/1.1", "GET /v1/tra\u0001nsfers HTTP/1.1",
        "GET /v1/transfers%00 HTTP/1.1"})
    void aRequestTheServerCannotParseRevealsNothing(String requestLine) throws Exception {
        var response = overASocket(requestLine + "\r\nHost: localhost\r\nConnection: close\r\n\r\n");

        assertThat(response).startsWith("HTTP/1.1 400 ");
        assertThat(response).doesNotContainIgnoringCase("tomcat").doesNotContainIgnoringCase("apache")
                .doesNotContain("Exception").doesNotContain("\tat ").doesNotContainPattern("[0-9]+\\.[0-9]+\\.[0-9]+");
    }

    /** Not valid as a request for the application, so the server answers these itself, without the chain and in its HTML. */
    @ParameterizedTest(name = "{0}, {1}")
    @CsvSource({
        "'CONNECT somewhere:1 HTTP/1.1', 501, ''",
        "'GET /v1/transfers HTTP/1.1', 417, 'Expect: nonsense'",
        "'GET /v1/transfers HTTP/3.0', 505, ''",
        "'GET /v1/transfers HTTP/1.1', 400, 'X-Too-Large: REPEAT'"})
    void whatTheServerAnswersItselfRevealsNothing(String requestLine, int status, String header) throws Exception {
        var headerLine = header.replace("REPEAT", "a".repeat(9000));

        var answer = overASocket(requestLine + "\r\nHost: localhost\r\nConnection: close\r\n"
                + (headerLine.isEmpty() ? "" : headerLine + "\r\n") + "\r\n");

        assertThat(answer).startsWith("HTTP/1.1 " + status + " ").containsIgnoringCase("content-type: text/html");
        assertThat(answer).doesNotContainIgnoringCase("tomcat").doesNotContainIgnoringCase("apache")
                .doesNotContain("Exception").doesNotContain("\tat ").doesNotContainPattern("[0-9]+\\.[0-9]+\\.[0-9]+");
    }

    /** The server keeps these from every client, ahead of the chain, so nobody finds out more here than with a principal. */
    @ParameterizedTest
    @ValueSource(strings = {"/WEB-INF/web.xml", "/WEB-INF", "/META-INF/MANIFEST.MF"})
    void theFoldersTheServerProtectsAnswerThatNothingIsThere(String path) throws Exception {
        var response = send(request(path));

        assertProblem(response, ProblemType.NOT_FOUND, path);
    }

    /** It names no route, so the rule that demands a principal has nothing to apply to, and it reveals only the methods. */
    @Test
    void optionsForTheWholeServerIsAnsweredByTheServer() throws Exception {
        var answer = overASocket("OPTIONS * HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");

        assertThat(answer).startsWith("HTTP/1.1 200 ").containsIgnoringCase("content-length: 0")
                .containsIgnoringCase("allow: GET, HEAD, POST, PUT, DELETE, OPTIONS");
    }

    @Test
    void aPathLongerThanTheServerReadsRevealsNothing() throws Exception {
        var response = overASocket(
                "GET /" + "a".repeat(9000) + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");

        assertThat(response).startsWith("HTTP/1.1 400 ");
        assertThat(response).doesNotContainIgnoringCase("tomcat").doesNotContain("Exception").doesNotContain("aaaa");
    }

    private String overASocket(String request) throws IOException {
        return TestHttp.overASocket(port, request);
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
        return TestHttp.send(request);
    }
}
