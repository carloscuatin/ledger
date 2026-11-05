package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.carloscuatin.ledgerapp.LedgerApplication;
import com.carloscuatin.ledgerapp.ledger.adapters.out.persistence.LedgerDatabase;

/**
 * Which process opens a port. The context of this class is the api profile on a real server; the other processes are
 * started inside the tests that look at them, because they are what is being observed.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("api")
class ApiProfileIT {

    private static final String LOCALHOST = "127.0.0.1";

    private static final String SERVER_REFUSAL = "This process would open a port without the api profile, the only one"
            + " that declares the security filter chain: Spring Boot's default chain would serve the port instead.";

    private static final String REFUSAL = "The api and migrate profiles must not run in the same process: api serves"
            + " requests with the application's role, and migrate changes the schema with its own.";

    @DynamicPropertySource
    static void connectAsTheApplication(DynamicPropertyRegistry registry) {
        LedgerDatabase.connectAsTheApplication(registry);
    }

    @Autowired
    private ApplicationContext apiContext;

    @LocalServerPort
    private int port;

    /**
     * A JVM stays up while a thread that is not a daemon lives, which is how a web server keeps its process running.
     * This looks at the cause; that the packaged application then exits with code 0 can only be seen from outside it.
     */
    @Test
    void withoutAProfileTheProcessIsNotAServerAndNothingKeepsItAlive() {
        var before = nonDaemonThreads();

        try (var context = LedgerDatabase.startApplication(LedgerDatabase.url())) {
            assertThat(context).isNotInstanceOf(WebServerApplicationContext.class);
            assertThat(threadsSince(before)).isEmpty();
        }
    }

    @Test
    void theApiProfileIsAServerOnItsPort() {
        assertThat(apiContext).isInstanceOfSatisfying(WebServerApplicationContext.class,
                server -> assertThat(server.getWebServer().getPort()).isEqualTo(port));
    }

    @Test
    void theMigrateProfileIsNotAServerAndNothingKeepsItAlive() {
        var before = nonDaemonThreads();

        try (var context = LedgerDatabase.startMigrateProfile()) {
            assertThat(context).isNotInstanceOf(WebServerApplicationContext.class);
            assertThat(threadsSince(before)).isEmpty();
        }
    }

    /** Nothing may have happened by the time the process refuses: no port, and no change to an empty database. */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void theApiAndMigrateProfilesTogetherAreRefusedWithNothingDone(boolean lazy) throws Exception {
        var url = LedgerDatabase.createEmptyDatabaseForTheMigrator();
        var freePort = aFreePort();

        var opened = openedWhile(freePort, () -> assertThatThrownBy(() -> apiAndMigrate(LedgerApplication.class)
                .run(LedgerDatabase.argumentsAsTheMigrator(url, "--server.port=" + freePort,
                        "--spring.main.lazy-initialization=" + lazy)))
                .isInstanceOf(IllegalStateException.class).hasMessage(REFUSAL));

        assertThat(opened).isFalse();
        assertThat(LedgerDatabase.tableExists(url, "flyway_schema_history")).isFalse();
        assertThat(LedgerDatabase.tableExists(url, "transfer")).isFalse();
    }

    /**
     * A bean that demands the migration, declared ahead of everything the application declares, is the worst order
     * possible. If the refusal depended on the order of bean creation, this would migrate first.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void theRefusalComesBeforeEveryBeanWhateverTheirOrder(boolean lazy) throws Exception {
        var url = LedgerDatabase.createEmptyDatabaseForTheMigrator();
        var initialized = new ArrayList<String>();
        ApplicationContextInitializer<ConfigurableApplicationContext> recorder = context -> context.getBeanFactory()
                .addBeanPostProcessor(new BeanPostProcessor() {
                    @Override
                    public Object postProcessBeforeInitialization(Object bean, String name) {
                        initialized.add(name);
                        return bean;
                    }
                });

        assertThatThrownBy(
                () -> apiAndMigrate(DemandsTheMigration.class, LedgerApplication.class).initializers(recorder)
                        .run(LedgerDatabase.argumentsAsTheMigrator(url, "--server.port=0",
                                "--spring.main.lazy-initialization=" + lazy)))
                .isInstanceOf(IllegalStateException.class).hasMessage(REFUSAL);

        // The container's own post-processors do get created; what must not exist is anything the process would use.
        assertThat(initialized).contains("apiAndMigrateRefusal").doesNotContain("demandsTheMigration", "flyway",
                "flywayInitializer", "dataSource", "databaseRoleGuard", "transfersController", "createTransfer",
                "tomcatServletWebServerFactory", "securityFilterChain");
        assertThat(LedgerDatabase.tableExists(url, "flyway_schema_history")).isFalse();
    }

    /**
     * Setting the web application type by hand makes a server of a process with no security chain of its own: Spring Boot
     * would then serve its own login form, and the migrate profile would never exit. Nothing may have happened by the time
     * the process refuses. The body closes what starts, so a process that wrongly starts does not outlive its test.
     */
    @ParameterizedTest(name = "profile [{0}], lazy {1}")
    @CsvSource({"'', false", "'', true", "migrate, false", "migrate, true"})
    void aServerWithoutTheApiProfileIsRefusedWithNothingDone(String profile, boolean lazy) throws Exception {
        var url = LedgerDatabase.createEmptyDatabaseForTheMigrator();
        var freePort = aFreePort();

        var opened = openedWhile(freePort, () -> assertThatThrownBy(() -> {
            try (var started = withProfile(profile, LedgerApplication.class)
                    .run(LedgerDatabase.argumentsAsTheMigrator(url,
                            "--server.port=" + freePort, "--spring.main.web-application-type=servlet",
                            "--spring.main.lazy-initialization=" + lazy))) {
                assertThat(started).isNotNull();
            }
        }).isInstanceOf(IllegalStateException.class).hasMessage(SERVER_REFUSAL));

        assertThat(opened).isFalse();
        assertThat(LedgerDatabase.tableExists(url, "flyway_schema_history")).isFalse();
        assertThat(LedgerDatabase.tableExists(url, "transfer")).isFalse();
    }

    /** The database a deployment has: without the refusal, nothing else stops this process from serving. */
    @ParameterizedTest(name = "lazy {0}")
    @ValueSource(booleans = {false, true})
    void aServerWithoutTheApiProfileIsRefusedOnAMigratedDatabase(boolean lazy) throws Exception {
        var freePort = aFreePort();

        var opened = openedWhile(freePort, () -> assertThatThrownBy(() -> {
            try (var started = new SpringApplicationBuilder(LedgerApplication.class).run(
                    "--spring.datasource.url=" + LedgerDatabase.url(), "--spring.datasource.username=ledger_app",
                    "--spring.datasource.password=test-only-app", "--server.port=" + freePort,
                    "--spring.main.web-application-type=servlet", "--spring.main.lazy-initialization=" + lazy)) {
                assertThat(started).isNotNull();
            }
        }).isInstanceOf(IllegalStateException.class).hasMessage(SERVER_REFUSAL));

        assertThat(opened).isFalse();
    }

    /** The same order as for the two profiles together, for the same reason. */
    @ParameterizedTest(name = "lazy {0}")
    @ValueSource(booleans = {false, true})
    void theRefusalOfAServerComesBeforeEveryBeanWhateverTheirOrder(boolean lazy) throws Exception {
        var url = LedgerDatabase.createEmptyDatabaseForTheMigrator();
        var initialized = new ArrayList<String>();
        ApplicationContextInitializer<ConfigurableApplicationContext> recorder = context -> context.getBeanFactory()
                .addBeanPostProcessor(new BeanPostProcessor() {
                    @Override
                    public Object postProcessBeforeInitialization(Object bean, String name) {
                        initialized.add(name);
                        return bean;
                    }
                });

        assertThatThrownBy(() -> {
            try (var started = withProfile("migrate", DemandsTheMigration.class, LedgerApplication.class)
                    .initializers(recorder).run(LedgerDatabase.argumentsAsTheMigrator(url, "--server.port=0",
                            "--spring.main.web-application-type=servlet",
                            "--spring.main.lazy-initialization=" + lazy))) {
                assertThat(started).isNotNull();
            }
        }).isInstanceOf(IllegalStateException.class).hasMessage(SERVER_REFUSAL);

        assertThat(initialized).contains("serverWithoutTheApiProfileRefusal").doesNotContain("demandsTheMigration",
                "flyway", "flywayInitializer", "dataSource", "databaseRoleGuard", "createTransfer",
                "tomcatServletWebServerFactory");
        assertThat(LedgerDatabase.tableExists(url, "flyway_schema_history")).isFalse();
    }

    /** What the refusal must leave alone, with the setting spelled out as well as left to its default. */
    @ParameterizedTest(name = "lazy {0}")
    @ValueSource(booleans = {false, true})
    void aProcessThatIsNotAServerIsNotRefused(boolean lazy) throws Exception {
        var url = LedgerDatabase.createEmptyDatabaseForTheMigrator();

        try (var migrate = withProfile("migrate", LedgerApplication.class)
                .run(LedgerDatabase.argumentsAsTheMigrator(url,
                        "--spring.main.web-application-type=none", "--spring.main.lazy-initialization=" + lazy))) {
            assertThat(migrate).isNotInstanceOf(WebServerApplicationContext.class);
        }
        assertThat(LedgerDatabase.tableExists(url, "transfer")).isTrue();
        try (var api = LedgerDatabase.startApi(List.of(), "--spring.main.lazy-initialization=" + lazy)) {
            assertThat(api).isInstanceOf(WebServerApplicationContext.class);
        }
    }

    /**
     * The probe knocks during the whole startup, because a port opened and closed again would look the same as one that
     * never opened if it only looked afterwards.
     */
    @Test
    void aRefusedProcessNamesTheCauseAndNeverOpensItsPort() throws Exception {
        var freePort = aFreePort();

        var opened = openedWhile(freePort, () -> assertThatThrownBy(() -> LedgerDatabase.startApi(
                LedgerDatabase.superuserName(), LedgerDatabase.superuserPassword(), freePort))
                .hasStackTraceContaining("it is a superuser"));

        assertThat(opened).isFalse();
        assertThatThrownBy(() -> new Socket(LOCALHOST, freePort).close()).isInstanceOf(ConnectException.class);
    }

    private static SpringApplicationBuilder withProfile(String profile, Class<?>... sources) {
        var application = new SpringApplicationBuilder(sources);
        return profile.isEmpty() ? application : application.profiles(profile);
    }

    private static SpringApplicationBuilder apiAndMigrate(Class<?>... sources) {
        return new SpringApplicationBuilder(sources).profiles("api", "migrate");
    }

    private static boolean openedWhile(int port, Runnable startup) throws InterruptedException {
        var finished = new AtomicBoolean();
        var opened = new AtomicBoolean();
        var probe = Thread.ofVirtual().start(() -> {
            while (!finished.get()) {
                opened.compareAndSet(false, answers(port));
            }
        });
        try {
            startup.run();
        } finally {
            finished.set(true);
            probe.join();
        }
        return opened.get();
    }

    private static int aFreePort() throws IOException {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static boolean answers(int port) {
        try (var socket = new Socket(LOCALHOST, port)) {
            return socket.isConnected();
        } catch (IOException _) {
            return false;
        }
    }

    private static Set<Thread> nonDaemonThreads() {
        var threads = new HashSet<>(Thread.getAllStackTraces().keySet());
        threads.removeIf(Thread::isDaemon);
        return threads;
    }

    private static List<String> threadsSince(Set<Thread> before) {
        return nonDaemonThreads().stream().filter(thread -> !before.contains(thread)).map(Thread::getName).toList();
    }
}
