package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.framework.Advised;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.carloscuatin.ledgerapp.ledger.adapters.out.persistence.LedgerDatabase;
import com.carloscuatin.ledgerapp.ledger.adapters.out.persistence.TransferRows;
import com.carloscuatin.ledgerapp.ledger.application.CreateTransfer;
import com.carloscuatin.ledgerapp.ledger.application.CurrentTenant;
import com.carloscuatin.ledgerapp.ledger.application.Transfers;
import com.carloscuatin.ledgerapp.ledger.domain.TenantId;
import com.zaxxer.hikari.HikariDataSource;

import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * What a thread holds at each point of a request. The first transaction, and the first persistence session, must belong
 * to the use case, because only there is a tenant in scope.
 * <p>
 * The context starts here and not through the test framework, which might reuse one that started before the output was
 * being captured: the warning about an open session is printed once, at startup. An interceptor runs before and after
 * the controller, so a transaction the controller opens and closes is invisible to it; advice added to the proxies of
 * the use case and the port, outside their own transactions, is what sees that one.
 */
@ExtendWith(OutputCaptureExtension.class)
class TransactionBoundaryIT {

    private static final String TRANSFERS = "/v1/transfers";
    private static final int POOL_SIZE = 2;
    private static final int CONCURRENT_REQUESTS = 100;
    private static final Duration POOL_QUEUE_DEADLINE = Duration.ofSeconds(20);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private static final String BEFORE_THE_CONTROLLER = "before the controller";
    private static final String BEFORE_THE_USE_CASE = "before the use case";
    private static final String IN_THE_PORT = "in the port";
    private static final String AFTER_THE_USE_CASE = "after the use case";
    private static final String AFTER_THE_CONTROLLER = "after the controller";

    private static final Queue<Moment> MOMENTS = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean HOLD_CONNECTIONS = new AtomicBoolean();
    private static final AtomicInteger HOLDERS = new AtomicInteger();
    private static final AtomicInteger LARGEST_QUEUE_SEEN = new AtomicInteger();

    private static ConfigurableApplicationContext api;
    private static HikariDataSource pool;

    private record Moment(String where, boolean transaction, boolean session, Optional<TenantId> tenant,
            Thread thread) {

        static void record(String where) {
            MOMENTS.add(
                    new Moment(where, TransactionSynchronizationManager.isActualTransactionActive(), sessionIsOpen(),
                            tenantInScope(), Thread.currentThread()));
        }

        /** A session is bound to the thread under its factory, whoever opened it. */
        private static boolean sessionIsOpen() {
            return TransactionSynchronizationManager.getResourceMap().keySet().stream()
                    .anyMatch(EntityManagerFactory.class::isInstance);
        }

        private static Optional<TenantId> tenantInScope() {
            try {
                return Optional.of(CurrentTenant.require());
            } catch (IllegalStateException _) {
                return Optional.empty();
            }
        }
    }

    /** Last of the interceptors going in, and so first coming out: what any other one opened is still open when it looks. */
    @TestConfiguration(proxyBeanMethods = false)
    static class AroundTheController implements WebMvcConfigurer {

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new HandlerInterceptor() {
                @Override
                public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                    Moment.record(BEFORE_THE_CONTROLLER);
                    return true;
                }

                @Override
                public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                        ModelAndView modelAndView) {
                    Moment.record(AFTER_THE_CONTROLLER);
                }
            }).order(Ordered.LOWEST_PRECEDENCE);
        }
    }

    @BeforeAll
    static void startTheServer() {
        api = LedgerDatabase.startApi(List.of(TestAuthentication.class, AroundTheController.class),
                "--spring.datasource.hikari.maximum-pool-size=" + POOL_SIZE);
        pool = api.getBean(HikariDataSource.class);
        ((Advised) api.getBean(CreateTransfer.class)).addAdvice(0, (MethodInterceptor) invocation -> {
            Moment.record(BEFORE_THE_USE_CASE);
            var result = invocation.proceed();
            Moment.record(AFTER_THE_USE_CASE);
            return result;
        });
        ((Advised) api.getBean(Transfers.class)).addAdvice(0, (MethodInterceptor) invocation -> {
            Moment.record(IN_THE_PORT);
            keepTheConnectionUntilAnotherRequestQueuesForIt();
            return invocation.proceed();
        });
    }

    @AfterAll
    static void stopTheServer() {
        api.close();
    }

    @BeforeEach
    void forgetEarlierRequests() {
        MOMENTS.clear();
    }

    /**
     * Only the first requests hold on, one per connection, which leaves every later one waiting for the pool: an overlap
     * that is certain rather than likely. The deadline is shorter than the pool's own, so requests that never queue fail
     * the assertion on the queue and not with a timeout of the pool.
     */
    private static void keepTheConnectionUntilAnotherRequestQueuesForIt() throws InterruptedException {
        if (!HOLD_CONNECTIONS.get() || HOLDERS.incrementAndGet() > POOL_SIZE) {
            return;
        }
        var deadline = Instant.now().plus(POOL_QUEUE_DEADLINE);
        while (pool.getHikariPoolMXBean().getThreadsAwaitingConnection() == 0 && Instant.now().isBefore(deadline)) {
            Thread.sleep(1);
        }
        LARGEST_QUEUE_SEEN.accumulateAndGet(pool.getHikariPoolMXBean().getThreadsAwaitingConnection(), Math::max);
    }

    @Test
    void theInterceptorOfAnOpenSessionIsNotThereAndItsWarningIsNotPrinted(CapturedOutput output) {
        assertThat(api.getBeansOfType(OpenEntityManagerInViewInterceptor.class)).isEmpty();
        assertThat(api.containsBean("openEntityManagerInViewInterceptor")).isFalse();
        assertThat(output.getAll()).contains(":: Spring Boot ::").doesNotContain("spring.jpa.open-in-view");
    }

    /** The port is the control: it sees a session and a transaction, so the probe would see them anywhere else too. */
    @Test
    void aSessionExistsOnlyInsideTheUseCase() throws Exception {
        assertThat(createATransferAs(UUID.randomUUID()).statusCode()).isEqualTo(201);

        assertThat(MOMENTS).extracting(Moment::where, Moment::session).containsExactly(
                tuple(BEFORE_THE_CONTROLLER, false),
                tuple(BEFORE_THE_USE_CASE, false),
                tuple(IN_THE_PORT, true),
                tuple(AFTER_THE_USE_CASE, false),
                tuple(AFTER_THE_CONTROLLER, false));
    }

    @Test
    void theControllerRunsOutsideATransactionAndTheUseCaseOpensItWithATenant() throws Exception {
        var tenant = UUID.randomUUID();
        var inScope = Optional.of(new TenantId(tenant));

        assertThat(createATransferAs(tenant).statusCode()).isEqualTo(201);

        assertThat(MOMENTS).extracting(Moment::where, Moment::transaction, Moment::tenant).containsExactly(
                tuple(BEFORE_THE_CONTROLLER, false, Optional.empty()),
                tuple(BEFORE_THE_USE_CASE, false, inScope),
                tuple(IN_THE_PORT, true, inScope),
                tuple(AFTER_THE_USE_CASE, false, inScope),
                tuple(AFTER_THE_CONTROLLER, false, Optional.empty()));
    }

    @Test
    void aRequestRunsOnOneVirtualThreadOfTheServer() throws Exception {
        assertThat(createATransferAs(UUID.randomUUID()).statusCode()).isEqualTo(201);

        assertThat(MOMENTS).hasSize(5).extracting(Moment::thread).containsOnly(MOMENTS.element().thread());
        assertThat(MOMENTS.element().thread().isVirtual()).isTrue();
        assertThat(MOMENTS.element().thread().getName()).startsWith("tomcat-handler-");
    }

    /**
     * A tenant travels in a scoped value of the thread and in a setting of the connection, and both are reused here: many
     * virtual threads share a few carriers, and take turns on a pool smaller than the crowd. A row in the wrong tenant
     * raises no error, so the table is looked at as the superuser.
     */
    @Test
    void requestsOfManyTenantsAtOnceKeepTheirTenantsOnASmallPool() throws Exception {
        var tenants = new ArrayList<UUID>();
        var responses = new ArrayList<CompletableFuture<HttpResponse<String>>>();
        HOLD_CONNECTIONS.set(true);
        try {
            for (var i = 0; i < CONCURRENT_REQUESTS; i++) {
                var tenant = UUID.randomUUID();
                tenants.add(tenant);
                responses.add(CLIENT.sendAsync(creatingATransferAs(tenant), BodyHandlers.ofString()));
            }
            CompletableFuture.allOf(responses.toArray(CompletableFuture[]::new)).join();
        } finally {
            HOLD_CONNECTIONS.set(false);
        }

        assertThat(LARGEST_QUEUE_SEEN).hasPositiveValue();
        for (var i = 0; i < CONCURRENT_REQUESTS; i++) {
            var response = responses.get(i).join();
            assertThat(response.statusCode()).isEqualTo(201);
            var id = UUID.fromString(JSON.readTree(response.body()).path("id").asString());
            assertThat(TransferRows.tenantOf(id)).isEqualTo(tenants.get(i));
        }
        assertThat(MOMENTS).extracting(Moment::thread).allMatch(Thread::isVirtual);
        assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
    }

    private static HttpResponse<String> createATransferAs(UUID tenant) throws Exception {
        return CLIENT.send(creatingATransferAs(tenant), BodyHandlers.ofString());
    }

    private static HttpRequest creatingATransferAs(UUID tenant) {
        var port = ((WebServerApplicationContext) api).getWebServer().getPort();
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:%d%s".formatted(port, TRANSFERS)))
                .header(TestAuthentication.PRINCIPAL_HEADER, tenant.toString());
        return AuthenticatedApi.postingJson(request, AuthenticatedApi.aValidBody()).build();
    }
}
