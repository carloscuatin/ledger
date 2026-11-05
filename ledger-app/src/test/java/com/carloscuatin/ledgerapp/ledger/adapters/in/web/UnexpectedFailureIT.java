package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import static com.carloscuatin.ledgerapp.ledger.adapters.in.web.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.filter.OncePerRequestFilter;

import com.carloscuatin.ledgerapp.ledger.adapters.out.persistence.LedgerDatabase;
import com.carloscuatin.ledgerapp.ledger.application.CreateTransfer;
import com.carloscuatin.ledgerapp.ledger.domain.CurrencyMismatchException;
import com.zaxxer.hikari.HikariDataSource;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

/**
 * Two readers want opposite things from a failure of this side: operators need all of it in the log, and whoever sent
 * the request must get none of it.
 */
@ExtendWith(OutputCaptureExtension.class)
class UnexpectedFailureIT extends AuthenticatedApi {

    private static final String TRANSFERS = "/v1/transfers";
    private static final String MARK = "SYNTHETICMARK";
    private static final String FAIL_IN_A_FILTER = "X-Test-Fail-In-Filter";
    private static final String CLIENT_HANGS_UP = "X-Test-Client-Hangs-Up";

    /** Spring applies this to the production chain, in this class's context only. */
    @TestConfiguration(proxyBeanMethods = false)
    static class AFilterThatFails {

        @Bean
        Customizer<HttpSecurity> filterThatFailsOnDemand() {
            return http -> http.addFilterBefore(new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                        FilterChain chain) throws ServletException, IOException {
                    if (request.getHeader(FAIL_IN_A_FILTER) != null) {
                        throw new IllegalStateException("SELECT secret FROM " + MARK);
                    }
                    chain.doFilter(request, response);
                }
            }, AnonymousAuthenticationFilter.class);
        }
    }

    /**
     * Whether a client's hang-up reaches the server while it writes depends on the buffers of two machines, so a test
     * cannot provoke it on a real socket every time. This fails the write the way the server does when it happens, with
     * the real framework above it.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class AClientThatHangsUp {

        @Bean
        Customizer<HttpSecurity> responseThatFailsOnDemand() {
            return http -> http.addFilterBefore(new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                        FilterChain chain) throws ServletException, IOException {
                    chain.doFilter(request, request.getHeader(CLIENT_HANGS_UP) == null ? response
                            : new HungUpResponse(response));
                }
            }, AnonymousAuthenticationFilter.class);
        }
    }

    private static final class HungUpResponse extends HttpServletResponseWrapper {

        HungUpResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public boolean isCommitted() {
            return true;
        }

        @Override
        public ServletOutputStream getOutputStream() {
            return new ServletOutputStream() {
                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener listener) {
                }

                @Override
                public void write(int value) throws IOException {
                    throw new ClientAbortException(new IOException("Broken pipe"));
                }
            };
        }
    }

    @MockitoBean
    private CreateTransfer createTransfer;

    @Test
    void aClientThatHangsUpWhileBeingAnsweredIsNotAFailure(CapturedOutput output) throws Exception {
        send(requestAs(UUID.randomUUID(), TRANSFERS).header(CLIENT_HANGS_UP, "yes"));

        assertThat(output).doesNotContain(" ERROR ").doesNotContain(" WARN ").doesNotContain("Broken pipe");
    }

    @Test
    void aFailingUseCaseRevealsNothingAndIsLoggedWithItsTrace(CapturedOutput output) throws Exception {
        when(createTransfer.create(any())).thenThrow(new IllegalStateException("SELECT secret FROM " + MARK));

        var response = send(postingJson(requestAs(UUID.randomUUID(), TRANSFERS), aValidBody()));

        assertProblem(response, ProblemType.INTERNAL_ERROR, TRANSFERS);
        assertThat(response.body()).doesNotContain(MARK).doesNotContain("IllegalStateException");
        assertThat(output).contains(" ERROR ").contains("java.lang.IllegalStateException: SELECT secret FROM " + MARK)
                .contains("at " + getClass().getPackageName());
    }

    /** A rule of the domain the API has no answer for is a mistake in this code, not something a client can correct. */
    @Test
    void aDomainRuleTheApiNeverExpectsIsAFailureOfThisSide(CapturedOutput output) throws Exception {
        var failure = new CurrencyMismatchException(Currency.getInstance("COP"), Currency.getInstance("USD"));
        when(createTransfer.create(any())).thenThrow(failure);

        var response = send(postingJson(requestAs(UUID.randomUUID(), TRANSFERS), aValidBody()));

        assertProblem(response, ProblemType.INTERNAL_ERROR, TRANSFERS);
        assertThat(response.body()).doesNotContain("USD");
        assertThat(output).contains(" ERROR ").contains(CurrencyMismatchException.class.getName());
    }

    /** Before the rule that demands a principal runs, so a request without one gets this answer too. */
    @Test
    void aFailingFilterRevealsNothingAndIsLoggedWithItsTrace(CapturedOutput output) throws Exception {
        var withPrincipal = send(requestAs(UUID.randomUUID(), TRANSFERS).header(FAIL_IN_A_FILTER, "yes"));
        var withoutPrincipal = send(request(TRANSFERS).header(FAIL_IN_A_FILTER, "yes"));

        assertThat(List.of(withPrincipal, withoutPrincipal)).allSatisfy(response -> {
            var problem = assertProblem(response, ProblemType.INTERNAL_ERROR, TRANSFERS);
            assertThat(problem.has("trace")).isFalse();
            assertThat(response.body() + response.headers().map()).doesNotContain(MARK)
                    .doesNotContain("IllegalStateException");
        });
        assertThat(output).contains(" ERROR ").contains("java.lang.IllegalStateException: SELECT secret FROM " + MARK)
                .contains("at " + getClass().getName());
    }

    /**
     * A context of its own, because this breaks its pool. Two failures, since they tell the log different things: a closed
     * pool's message has nothing about the database in it, and only the refused login, whose root cause names the role and
     * carries an SQL state, gives the response something it could repeat. A pool of one, because a larger one is still
     * opening connections in the background after the start, and one already opening when the password changes succeeds.
     */
    @Test
    void aDatabaseThatFailsRevealsNothing(CapturedOutput output) throws Exception {
        try (var api = LedgerDatabase.startApi(List.of(TestAuthentication.class),
                "--spring.datasource.hikari.maximum-pool-size=1",
                "--spring.datasource.hikari.connection-timeout=250")) {
            var pool = api.getBean(HikariDataSource.class);

            pool.getHikariConfigMXBean().setPassword("not-the-password");
            pool.getHikariPoolMXBean().softEvictConnections();
            assertRevealsNothingOfTheDatabase(createATransferOn(api));
            assertThat(output).contains(" ERROR ").contains("password authentication failed for user \"ledger_app\"")
                    .contains("SQLState: 28P01");

            pool.close();
            assertRevealsNothingOfTheDatabase(createATransferOn(api));
            assertThat(output).contains(" ERROR ").contains("has been closed");
        }
        assertThat(output).doesNotContain("test-only-app").doesNotContain("not-the-password");
    }

    private static void assertRevealsNothingOfTheDatabase(HttpResponse<String> response) {
        assertProblem(response, ProblemType.INTERNAL_ERROR, TRANSFERS);
        var host = URI.create(LedgerDatabase.url().substring("jdbc:".length()));
        assertThat(response.body() + response.headers().map()).doesNotContain(host.getHost())
                .doesNotContain(String.valueOf(host.getPort())).doesNotContain("jdbc:").doesNotContain("ledger_app")
                .doesNotContain("28P01").doesNotContain("SQL").doesNotContain("Hikari").doesNotContain("Exception");
    }

    private static HttpResponse<String> createATransferOn(ConfigurableApplicationContext api)
            throws IOException, InterruptedException {
        var port = ((WebServerApplicationContext) api).getWebServer().getPort();
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:%d%s".formatted(port, TRANSFERS)))
                .header(TestAuthentication.PRINCIPAL_HEADER, UUID.randomUUID().toString());
        return TestHttp.send(postingJson(request, aValidBody()));
    }
}
