package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.carloscuatin.ledgerapp.ledger.adapters.out.persistence.LedgerDatabase;

/**
 * Which process opens a port. The context of this class is the api profile on a real server; the other processes are
 * started inside the tests that look at them, because they are what is being observed.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("api")
class ApiProfileIT {

    private static final String LOCALHOST = "127.0.0.1";

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

    @Test
    @Disabled("Starts until a controller depends on a use case, which this combination of profiles does not build")
    void theApiAndMigrateProfilesTogetherDoNotStart() {
        assertThatThrownBy(LedgerDatabase::startApiAndMigrateProfiles)
                .isInstanceOf(BeanCreationException.class);
    }

    /**
     * The probe knocks during the whole startup, because a port opened and closed again would look the same as one that
     * never opened if it only looked afterwards.
     */
    @Test
    void aRefusedProcessNamesTheCauseAndNeverOpensItsPort() throws Exception {
        var freePort = aFreePort();
        var refused = new AtomicBoolean();
        var opened = new AtomicBoolean();
        var probe = Thread.ofVirtual().start(() -> {
            while (!refused.get()) {
                opened.compareAndSet(false, answers(freePort));
            }
        });

        assertThatThrownBy(() -> LedgerDatabase.startApi(LedgerDatabase.superuserName(),
                LedgerDatabase.superuserPassword(), freePort)).hasStackTraceContaining("it is a superuser");
        refused.set(true);
        probe.join();

        assertThat(opened).isFalse();
        assertThatThrownBy(() -> new Socket(LOCALHOST, freePort).close()).isInstanceOf(ConnectException.class);
    }

    private static int aFreePort() throws IOException {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static boolean answers(int port) {
        try (var socket = new Socket(LOCALHOST, port)) {
            return socket.isConnected();
        } catch (IOException noOneListening) {
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
