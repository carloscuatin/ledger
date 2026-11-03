package com.carloscuatin.ledgerapp.ledger.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.carloscuatin.ledgerapp.ledger.application.CreateTransfer;
import com.carloscuatin.ledgerapp.ledger.application.IdGenerator;
import com.carloscuatin.ledgerapp.ledger.application.Transfers;

import jakarta.persistence.EntityManagerFactory;

/**
 * The profile runs for real when {@link LedgerDatabase} starts, the way a deployment runs it, so these tests read what
 * it left behind in the database.
 */
class MigrateProfileIT {

    private static final String LAZY_INITIALIZATION = "--spring.main.lazy-initialization=true";

    @Test
    void theMigrateProfileRecordsTheMigrationAsApplied() throws SQLException {
        try (var connection = LedgerDatabase.asSuperuser();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("""
                        SELECT version, success, installed_by
                        FROM flyway_schema_history
                        WHERE version = '1'
                        """)) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getBoolean("success")).isTrue();
            assertThat(rows.getString("installed_by")).isEqualTo("ledger_migrator");
        }
    }

    @Test
    void theMigratorOwnsTheTable() throws SQLException {
        try (var connection = LedgerDatabase.asSuperuser();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("""
                        SELECT tableowner
                        FROM pg_tables
                        WHERE schemaname = 'public' AND tablename = 'transfer'
                        """)) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("ledger_migrator");
        }
    }

    @Test
    void runningTheMigrateProfileAgainAppliesNothing() throws SQLException {
        var before = appliedMigrations();

        LedgerDatabase.startMigrateProfile().close();

        assertThat(appliedMigrations()).isPositive().isEqualTo(before);
    }

    @Test
    void aProcessWithoutTheProfileHasNoFlyway() {
        try (var context = LedgerDatabase.startApplication(LedgerDatabase.url())) {
            assertThat(context.getEnvironment().getActiveProfiles()).isEmpty();
            assertThat(context.getBeansOfType(Flyway.class)).isEmpty();
        }
    }

    @Test
    void aProcessWithoutTheProfileRefusesAnEmptyDatabaseAndLeavesItWithoutAMigrationHistory() throws SQLException {
        var url = LedgerDatabase.createEmptyDatabase();

        assertThatThrownBy(() -> LedgerDatabase.startApplication(url))
                .hasStackTraceContaining("Schema validation: missing table [transfer]");

        try (var connection = LedgerDatabase.asSuperuser(url);
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT to_regclass('public.flyway_schema_history')")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isNull();
        }
    }

    @Test
    void theMigrateProcessBuildsNeitherTheOrmNorAUseCaseNorItsPorts() {
        try (var context = LedgerDatabase.startMigrateProfile()) {
            assertThat(context.getBeansOfType(EntityManagerFactory.class)).isEmpty();
            assertThat(context.getBeansOfType(CreateTransfer.class)).isEmpty();
            assertThat(context.getBeansOfType(IdGenerator.class)).isEmpty();
            assertThat(context.getBeansOfType(Transfers.class)).isEmpty();
            assertThat(context.getBeansOfType(DatabaseRoleGuard.class)).isEmpty();
        }
    }

    /** Without this, a wrong location finds nothing to apply and the deployment goes on with a schema nobody migrated. */
    @Test
    void theMigrateProfileFailsWhenTheLocationOfItsMigrationsDoesNotExist() throws SQLException {
        var url = LedgerDatabase.createEmptyDatabaseForTheMigrator();

        assertThatThrownBy(() -> LedgerDatabase.startMigrateProfile(url, "--spring.flyway.locations=classpath:missing"))
                .hasStackTraceContaining("Unable to resolve location classpath:missing");
    }

    /**
     * Migrating happens when the initializer is created, and in this process nothing else asks for it: a lazy context
     * would start, do nothing and report success.
     */
    @Test
    void theMigrateProfileMigratesWithLazyInitialization() throws SQLException {
        var url = LedgerDatabase.createEmptyDatabaseForTheMigrator();

        LedgerDatabase.startMigrateProfile(url, LAZY_INITIALIZATION).close();

        assertThat(LedgerDatabase.appliedMigrations(url)).contains("1");
        assertThat(LedgerDatabase.tableExists(url, "transfer")).isTrue();
    }

    /** Switching Flyway off is a deliberate choice of whoever runs the process, and it must keep meaning just that. */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void theMigrateProfileWithFlywaySwitchedOffStartsAndMigratesNothing(boolean lazy) throws SQLException {
        var url = LedgerDatabase.createEmptyDatabaseForTheMigrator();

        LedgerDatabase.startMigrateProfile(url, "--spring.flyway.enabled=false",
                "--spring.main.lazy-initialization=" + lazy).close();

        assertThat(LedgerDatabase.tableExists(url, "flyway_schema_history")).isFalse();
        assertThat(LedgerDatabase.tableExists(url, "transfer")).isFalse();
    }

    private static int appliedMigrations() throws SQLException {
        try (var connection = LedgerDatabase.asSuperuser();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT count(*) FROM flyway_schema_history")) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
