package com.carloscuatin.ledgerapp.ledger.adapters.out.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.carloscuatin.ledgerapp.LedgerApplication;

/**
 * One real PostgreSQL per test run, shared by every database test. The container starts when this class is first used
 * and Testcontainers removes it when the JVM exits; tests stay isolated by using tenants of their own, not by having
 * their own server. The image is the oldest version the ledger supports, so nothing newer can be relied on by accident.
 * The schema comes from the same migrate profile a deployment runs, so that path is exercised by every test run.
 */
public final class LedgerDatabase {

    private static final PostgreSQLContainer SERVER = new PostgreSQLContainer("postgres:16.15-alpine");

    static {
        SERVER.withInitScript("db/roles.sql");
        SERVER.start();
        startMigrateProfile().close();
    }

    private LedgerDatabase() {
    }

    /** Hands a Spring context the same settings a deployment gets from its environment, with the application role. */
    public static void connectAsTheApplication(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SERVER::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "ledger_app");
        registry.add("spring.datasource.password", () -> "test-only-app");
    }

    /** Starts what a deployment runs before the application, as a context the caller closes. */
    public static ConfigurableApplicationContext startMigrateProfile() {
        return new SpringApplicationBuilder(LedgerApplication.class).profiles("migrate")
                .run(arguments(SERVER.getJdbcUrl(), "ledger_migrator", "test-only-migrator"));
    }

    /** Starts the migrate profile on another database, with extra command line arguments. */
    static ConfigurableApplicationContext startMigrateProfile(String url, String... extraArguments) {
        return new SpringApplicationBuilder(LedgerApplication.class).profiles("migrate")
                .run(arguments(url, "ledger_migrator", "test-only-migrator", extraArguments));
    }

    /** Starts the application without a profile on a database, as a context the caller closes. */
    public static ConfigurableApplicationContext startApplication(String url) {
        return new SpringApplicationBuilder(LedgerApplication.class)
                .run(arguments(url, "ledger_app", "test-only-app"));
    }

    /** Starts the server on a port, 0 for any free one, as a context the caller closes. */
    public static ConfigurableApplicationContext startApi(String username, String password, int port) {
        return new SpringApplicationBuilder(LedgerApplication.class).profiles("api").run(
                arguments(SERVER.getJdbcUrl(), username, password, "--server.port=" + port));
    }

    /**
     * The server as the application's role, with what a test adds to it, for a test that needs the whole start of the
     * process in front of it or must be free to break the process. Spring caches a context it starts for a test class and
     * shares it, which is why those tests do not use one.
     */
    public static ConfigurableApplicationContext startApi(List<Class<?>> extraSources, String... extraArguments) {
        var arguments = new ArrayList<>(List.of(extraArguments));
        arguments.add("--server.port=0");
        return new SpringApplicationBuilder(LedgerApplication.class).sources(extraSources.toArray(Class<?>[]::new))
                .profiles("api")
                .run(arguments(SERVER.getJdbcUrl(), "ledger_app", "test-only-app", arguments.toArray(String[]::new)));
    }

    /** The command line of a process that connects to a database as the migrator. */
    public static String[] argumentsAsTheMigrator(String url, String... extraArguments) {
        return arguments(url, "ledger_migrator", "test-only-migrator", extraArguments);
    }

    /**
     * Tries to start the application with any role on the migrated database. Only for tests of the check that refuses
     * the wrong roles; every other context runs as the application role.
     */
    static ConfigurableApplicationContext startApplicationAs(String username, String password) {
        return startApplicationAs(SERVER.getJdbcUrl(), username, password);
    }

    static ConfigurableApplicationContext startApplicationAs(String url, String username, String password) {
        return new SpringApplicationBuilder(LedgerApplication.class).run(arguments(url, username, password));
    }

    /** With lazy initialization, the setting under which a bean that nothing depends on is never created. */
    static ConfigurableApplicationContext startLazyApplicationAs(String username, String password) {
        return new SpringApplicationBuilder(LedgerApplication.class).run(
                arguments(SERVER.getJdbcUrl(), username, password, "--spring.main.lazy-initialization=true"));
    }

    public static String superuserName() {
        return SERVER.getUsername();
    }

    public static String superuserPassword() {
        return SERVER.getPassword();
    }

    public static String url() {
        return SERVER.getJdbcUrl();
    }

    /** A new database on the same server, without a migration, and returns its URL. */
    static String createEmptyDatabase() throws SQLException {
        var name = "empty_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = asSuperuser(); var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        }
        return urlOf(name);
    }

    /** An empty database where the migrator may create objects, which it can only do after being granted that. */
    public static String createEmptyDatabaseForTheMigrator() throws SQLException {
        var url = createEmptyDatabase();
        try (var connection = asSuperuser(url); var statement = connection.createStatement()) {
            statement.execute("GRANT USAGE, CREATE ON SCHEMA public TO ledger_migrator");
        }
        return url;
    }

    /** The versions a database records as successfully applied, empty when it has no migration history at all. */
    public static List<String> appliedMigrations(String url) throws SQLException {
        if (!tableExists(url, "flyway_schema_history")) {
            return List.of();
        }
        try (var connection = asSuperuser(url);
                var statement = connection.createStatement();
                var rows = statement.executeQuery(
                        "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank")) {
            var versions = new ArrayList<String>();
            while (rows.next()) {
                versions.add(rows.getString(1));
            }
            return versions;
        }
    }

    public static boolean tableExists(String url, String table) throws SQLException {
        try (var connection = asSuperuser(url);
                var statement = connection.prepareStatement("SELECT to_regclass(?)")) {
            statement.setString(1, "public." + table);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getString(1) != null;
            }
        }
    }

    static String urlOf(String database) {
        return "jdbc:postgresql://%s:%d/%s".formatted(
                SERVER.getHost(), SERVER.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT), database);
    }

    private static String[] arguments(String url, String username, String password, String... extraArguments) {
        var arguments = new ArrayList<>(List.of(
                "--spring.datasource.url=" + url,
                "--spring.datasource.username=" + username,
                "--spring.datasource.password=" + password));
        arguments.addAll(List.of(extraArguments));
        return arguments.toArray(String[]::new);
    }

    static Connection as(String username, String password) throws SQLException {
        return DriverManager.getConnection(SERVER.getJdbcUrl(), username, password);
    }

    static Connection asApplication() throws SQLException {
        return DriverManager.getConnection(SERVER.getJdbcUrl(), "ledger_app", "test-only-app");
    }

    static Connection asApplication(String url) throws SQLException {
        return DriverManager.getConnection(url, "ledger_app", "test-only-app");
    }

    /** The role that owns the tables and runs the migrations. */
    static Connection asMigrator() throws SQLException {
        return DriverManager.getConnection(SERVER.getJdbcUrl(), "ledger_migrator", "test-only-migrator");
    }

    /** Sees past row level security, so it can only ever be a control that checks what really is in a table. */
    public static Connection asSuperuser() throws SQLException {
        return asSuperuser(SERVER.getJdbcUrl());
    }

    static Connection asSuperuser(String url) throws SQLException {
        return DriverManager.getConnection(url, SERVER.getUsername(), SERVER.getPassword());
    }
}
