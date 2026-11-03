package com.carloscuatin.ledgerapp.ledger.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import com.carloscuatin.ledgerapp.ledger.application.CreateTransfer;
import com.carloscuatin.ledgerapp.ledger.application.IdGenerator;

import jakarta.persistence.EntityManagerFactory;

/**
 * The profile runs for real when {@link LedgerDatabase} starts, the way a deployment runs it, so these tests read what
 * it left behind in the database.
 */
class MigrateProfileIT {

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
    void aProcessWithoutTheProfileLeavesAnEmptyDatabaseWithoutAMigrationHistory() throws SQLException {
        var url = LedgerDatabase.createEmptyDatabase();

        LedgerDatabase.startApplication(url).close();

        try (var connection = LedgerDatabase.asSuperuser(url);
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT to_regclass('public.flyway_schema_history')")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isNull();
        }
    }

    @Test
    void theMigrateProcessBuildsNeitherTheOrmNorAUseCaseNorAPort() {
        try (var context = LedgerDatabase.startMigrateProfile()) {
            assertThat(context.getBeansOfType(EntityManagerFactory.class)).isEmpty();
            assertThat(context.getBeansOfType(CreateTransfer.class)).isEmpty();
            assertThat(context.getBeansOfType(IdGenerator.class)).isEmpty();
        }
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
