package com.carloscuatin.ledgerapp.ledger.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import java.sql.Statement;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Each role is checked in the catalog before the application tries it, so a refusal cannot come from a role that is not
 * what the test believes it is. Row level security silently does not apply to these roles, which is why they must stop
 * the startup instead of appearing to work.
 */
class DatabaseRoleGuardIT {

    private static final String BYPASS_ROLE = "ledger_bypass";
    private static final String BYPASS_PASSWORD = "test-only-bypass";

    private static final String MEMBER_ROLE = "ledger_member";
    private static final String MEMBER_PASSWORD = "test-only-member";
    private static final String NOINHERIT_ROLE = "ledger_member_noinherit";
    private static final String NOINHERIT_PASSWORD = "test-only-member-noinherit";
    private static final String UNRELATED_ROLE = "ledger_unrelated";
    private static final String UNRELATED_PASSWORD = "test-only-unrelated";

    private static final String MEMBER_OF_SUPERUSER_ROLE = "ledger_member_of_superuser";
    private static final String MEMBER_OF_SUPERUSER_PASSWORD = "test-only-member-of-superuser";
    private static final String MEMBER_OF_BYPASS_ROLE = "ledger_member_of_bypass";
    private static final String MEMBER_OF_BYPASS_PASSWORD = "test-only-member-of-bypass";
    private static final String OWNER_ELSEWHERE_ROLE = "ledger_owner_elsewhere";
    private static final String OWNER_ELSEWHERE_PASSWORD = "test-only-owner-elsewhere";
    private static final String SCHEMA_OWNER_ROLE = "ledger_schema_owner";
    private static final String SCHEMA_OWNER_PASSWORD = "test-only-schema-owner";
    private static final String MEMBER_OF_SCHEMA_OWNER_ROLE = "ledger_member_of_schema_owner";
    private static final String MEMBER_OF_SCHEMA_OWNER_PASSWORD = "test-only-member-of-schema-owner";
    private static final String EMPTY_SCHEMA_OWNER_ROLE = "ledger_empty_schema_owner";
    private static final String EMPTY_SCHEMA_OWNER_PASSWORD = "test-only-empty-schema-owner";
    private static final String DATABASE_OWNER_ROLE = "ledger_database_owner";
    private static final String DATABASE_OWNER_PASSWORD = "test-only-database-owner";

    private static final String SCHEMA_ELSEWHERE = "ledger_elsewhere";
    private static final String OWNED_SCHEMA = "ledger_owned";
    private static final String EMPTY_SCHEMA = "ledger_empty";
    private static final String OWNED_DATABASE = "ledger_owned_database";

    private static final String MEMBER_OF_SUPERUSER_REASON = "it is a member of a superuser";
    private static final String MEMBER_OF_BYPASS_REASON = "it is a member of a role with BYPASSRLS";
    private static final String OWNER_REASON = "it owns, or is a member of the owner of, a table";
    private static final String SCHEMA_OWNER_REASON = "it owns, or is a member of the owner of, a schema that holds a table";

    @BeforeAll
    static void theRolesUnderTestExist() throws SQLException {
        try (var connection = LedgerDatabase.asSuperuser(); var statement = connection.createStatement()) {
            statement.execute("CREATE ROLE %s LOGIN PASSWORD '%s' NOSUPERUSER BYPASSRLS"
                    .formatted(BYPASS_ROLE, BYPASS_PASSWORD));
            statement.execute("CREATE ROLE %s LOGIN PASSWORD '%s' NOSUPERUSER NOBYPASSRLS INHERIT"
                    .formatted(MEMBER_ROLE, MEMBER_PASSWORD));
            statement.execute("GRANT ledger_migrator TO " + MEMBER_ROLE);
            statement.execute("CREATE ROLE %s LOGIN PASSWORD '%s' NOSUPERUSER NOBYPASSRLS NOINHERIT"
                    .formatted(NOINHERIT_ROLE, NOINHERIT_PASSWORD));
            statement.execute("GRANT ledger_migrator TO " + NOINHERIT_ROLE);
            statement.execute("CREATE ROLE %s LOGIN PASSWORD '%s' NOSUPERUSER NOBYPASSRLS"
                    .formatted(UNRELATED_ROLE, UNRELATED_PASSWORD));
            statement.execute("GRANT ledger_app TO " + UNRELATED_ROLE);

            createRole(statement, MEMBER_OF_SUPERUSER_ROLE, MEMBER_OF_SUPERUSER_PASSWORD);
            statement.execute("GRANT %s TO %s".formatted(LedgerDatabase.superuserName(), MEMBER_OF_SUPERUSER_ROLE));
            createRole(statement, MEMBER_OF_BYPASS_ROLE, MEMBER_OF_BYPASS_PASSWORD);
            statement.execute("GRANT %s TO %s".formatted(BYPASS_ROLE, MEMBER_OF_BYPASS_ROLE));

            createRole(statement, OWNER_ELSEWHERE_ROLE, OWNER_ELSEWHERE_PASSWORD);
            statement.execute("CREATE SCHEMA " + SCHEMA_ELSEWHERE);
            statement.execute("CREATE TABLE %s.held (id integer)".formatted(SCHEMA_ELSEWHERE));
            statement.execute("ALTER TABLE %s.held OWNER TO %s".formatted(SCHEMA_ELSEWHERE, OWNER_ELSEWHERE_ROLE));

            createRole(statement, SCHEMA_OWNER_ROLE, SCHEMA_OWNER_PASSWORD);
            statement.execute("CREATE SCHEMA %s AUTHORIZATION %s".formatted(OWNED_SCHEMA, SCHEMA_OWNER_ROLE));
            statement.execute("CREATE TABLE %s.held (id integer)".formatted(OWNED_SCHEMA));
            createRole(statement, MEMBER_OF_SCHEMA_OWNER_ROLE, MEMBER_OF_SCHEMA_OWNER_PASSWORD);
            statement.execute("GRANT %s TO %s".formatted(SCHEMA_OWNER_ROLE, MEMBER_OF_SCHEMA_OWNER_ROLE));

            createRole(statement, EMPTY_SCHEMA_OWNER_ROLE, EMPTY_SCHEMA_OWNER_PASSWORD);
            statement.execute("CREATE SCHEMA %s AUTHORIZATION %s".formatted(EMPTY_SCHEMA, EMPTY_SCHEMA_OWNER_ROLE));

            createRole(statement, DATABASE_OWNER_ROLE, DATABASE_OWNER_PASSWORD);
            statement.execute("CREATE DATABASE %s OWNER %s".formatted(OWNED_DATABASE, DATABASE_OWNER_ROLE));
        }
        try (var connection = LedgerDatabase.asSuperuser(LedgerDatabase.urlOf(OWNED_DATABASE));
                var statement = connection.createStatement()) {
            statement.execute("GRANT USAGE, CREATE ON SCHEMA public TO ledger_migrator");
        }
        LedgerDatabase.startMigrateProfile(LedgerDatabase.urlOf(OWNED_DATABASE)).close();
    }

    private static void createRole(Statement statement, String role, String password) throws SQLException {
        statement.execute("CREATE ROLE %s LOGIN PASSWORD '%s' NOSUPERUSER NOBYPASSRLS".formatted(role, password));
    }

    /** Roles belong to the whole server, which other test classes share. */
    @AfterAll
    static void theRolesUnderTestAreGone() throws SQLException {
        try (var connection = LedgerDatabase.asSuperuser(); var statement = connection.createStatement()) {
            statement.execute("DROP DATABASE %s WITH (FORCE)".formatted(OWNED_DATABASE));
            statement.execute("DROP SCHEMA %s, %s, %s CASCADE".formatted(SCHEMA_ELSEWHERE, OWNED_SCHEMA, EMPTY_SCHEMA));
            statement.execute("DROP ROLE " + String.join(", ", MEMBER_OF_BYPASS_ROLE, BYPASS_ROLE, MEMBER_ROLE,
                    NOINHERIT_ROLE, UNRELATED_ROLE, MEMBER_OF_SUPERUSER_ROLE, OWNER_ELSEWHERE_ROLE,
                    MEMBER_OF_SCHEMA_OWNER_ROLE, SCHEMA_OWNER_ROLE, EMPTY_SCHEMA_OWNER_ROLE, DATABASE_OWNER_ROLE));
        }
    }

    private static void assertMembershipOfTheOwner(String role, boolean member, boolean usage) throws SQLException {
        try (var connection = LedgerDatabase.asSuperuser();
                var statement = connection.prepareStatement("""
                        SELECT pg_has_role(?, 'ledger_migrator', 'MEMBER'), pg_has_role(?, 'ledger_migrator', 'USAGE')
                        """)) {
            statement.setString(1, role);
            statement.setString(2, role);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBoolean(1)).isEqualTo(member);
                assertThat(rows.getBoolean(2)).isEqualTo(usage);
            }
        }
    }

    private static void assertRole(String role, boolean superuser, boolean bypassesRls, boolean ownsATable)
            throws SQLException {
        try (var connection = LedgerDatabase.asSuperuser();
                var statement = connection.prepareStatement("""
                        SELECT r.rolsuper, r.rolbypassrls,
                               EXISTS (SELECT 1 FROM pg_tables t
                                       WHERE t.schemaname = 'public' AND t.tableowner = r.rolname) AS owner
                        FROM pg_roles r
                        WHERE r.rolname = ?
                        """)) {
            statement.setString(1, role);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBoolean("rolsuper")).isEqualTo(superuser);
                assertThat(rows.getBoolean("rolbypassrls")).isEqualTo(bypassesRls);
                assertThat(rows.getBoolean("owner")).isEqualTo(ownsATable);
            }
        }
    }

    private static void assertRefused(String role, String password, String reason, String secret) {
        assertRefused(() -> LedgerDatabase.startApplicationAs(role, password), role, reason, secret);
    }

    private static void assertRefused(ThrowingCallable startup, String role, String reason, String secret) {
        assertThatThrownBy(startup)
                .hasRootCauseExactlyInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("Refusing to start with database role '%s': %s", role, reason)
                .satisfies(failure -> {
                    var trace = new StringWriter();
                    failure.printStackTrace(new PrintWriter(trace));
                    assertThat(trace.toString()).doesNotContain(secret).doesNotContain("jdbc:postgresql");
                });
    }

    @Test
    void theApplicationDoesNotStartAsASuperuser() throws SQLException {
        var role = LedgerDatabase.superuserName();
        assertRole(role, true, true, false);

        // The password of the container's superuser is also part of its name, so the URL is what is checked here.
        assertRefused(role, LedgerDatabase.superuserPassword(), "it is a superuser", "jdbc:postgresql");
    }

    @Test
    void theApplicationDoesNotStartWithARoleThatBypassesRowLevelSecurity() throws SQLException {
        assertRole(BYPASS_ROLE, false, true, false);

        assertRefused(BYPASS_ROLE, BYPASS_PASSWORD, "it has BYPASSRLS", BYPASS_PASSWORD);
    }

    @Test
    void theApplicationDoesNotStartAsTheOwnerOfATable() throws SQLException {
        assertRole("ledger_migrator", false, false, true);

        assertRefused("ledger_migrator", "test-only-migrator", OWNER_REASON, "test-only-migrator");
    }

    @Test
    void theApplicationStartsWithItsOwnRole() throws SQLException {
        assertRole("ledger_app", false, false, false);
        assertMembershipOfTheOwner("ledger_app", false, false);

        try (var context = LedgerDatabase.startApplicationAs("ledger_app", "test-only-app")) {
            assertThat(context.getBeansOfType(DatabaseRoleGuard.class)).hasSize(1);
            assertThat(context.getBean(JdbcClient.class).sql("SELECT current_user").query(String.class).single())
                    .isEqualTo("ledger_app");
        }
    }

    @Test
    void theApplicationDoesNotStartAsAMemberOfTheOwner() throws SQLException {
        assertRole(MEMBER_ROLE, false, false, false);
        assertMembershipOfTheOwner(MEMBER_ROLE, true, true);

        assertRefused(MEMBER_ROLE, MEMBER_PASSWORD, OWNER_REASON, MEMBER_PASSWORD);
    }

    @Test
    void theApplicationDoesNotStartAsAMemberOfTheOwnerThatDoesNotInherit() throws SQLException {
        assertRole(NOINHERIT_ROLE, false, false, false);
        assertMembershipOfTheOwner(NOINHERIT_ROLE, true, false);

        assertRefused(NOINHERIT_ROLE, NOINHERIT_PASSWORD, OWNER_REASON, NOINHERIT_PASSWORD);
    }

    /** Why inheriting does not decide it: a member that does not inherit can still become the owner and lift the policy. */
    @Test
    void aMemberThatDoesNotInheritCanStillBecomeTheOwnerAndDisableRowLevelSecurity() throws SQLException {
        try (var connection = LedgerDatabase.as(NOINHERIT_ROLE, NOINHERIT_PASSWORD);
                var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            assertThatThrownBy(() -> statement.execute("ALTER TABLE transfer DISABLE ROW LEVEL SECURITY"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("must be owner");
            connection.rollback();

            statement.execute("SET LOCAL ROLE ledger_migrator");
            statement.execute("ALTER TABLE transfer DISABLE ROW LEVEL SECURITY");

            try (var rows = statement.executeQuery(
                    "SELECT relrowsecurity FROM pg_class WHERE oid = 'public.transfer'::regclass")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBoolean(1)).isFalse();
            }
            // The rollback restores the table for every other test.
            connection.rollback();
        }
    }

    @Test
    void theApplicationStartsAsAMemberOfARoleThatOwnsNothing() throws SQLException {
        assertMembershipOfTheOwner(UNRELATED_ROLE, false, false);

        LedgerDatabase.startApplicationAs(UNRELATED_ROLE, UNRELATED_PASSWORD).close();
    }

    /** The check looks at what a role owns, and on an empty database nothing stands out; schema validation stops it. */
    @Test
    void theOwnerRoleOnAnEmptyDatabaseIsStoppedBySchemaValidation() throws SQLException {
        var url = LedgerDatabase.createEmptyDatabase();

        assertThatThrownBy(() -> LedgerDatabase.startApplicationAs(url, "ledger_migrator", "test-only-migrator"))
                .hasStackTraceContaining("Schema validation: missing table [transfer]");
    }

    /** Nothing depends on the guard, so a process with lazy initialization would otherwise never create it. */
    @Test
    void theApplicationDoesNotStartAsASuperuserWithLazyInitialization() {
        var role = LedgerDatabase.superuserName();

        assertRefused(() -> LedgerDatabase.startLazyApplicationAs(role, LedgerDatabase.superuserPassword()), role,
                "it is a superuser", "jdbc:postgresql");
    }

    @Test
    void theApplicationStartsWithItsOwnRoleWithLazyInitialization() {
        LedgerDatabase.startLazyApplicationAs("ledger_app", "test-only-app").close();
    }

    @Test
    void theApplicationDoesNotStartAsAMemberOfASuperuser() throws SQLException {
        assertRole(MEMBER_OF_SUPERUSER_ROLE, false, false, false);

        assertRefused(MEMBER_OF_SUPERUSER_ROLE, MEMBER_OF_SUPERUSER_PASSWORD, MEMBER_OF_SUPERUSER_REASON,
                MEMBER_OF_SUPERUSER_PASSWORD);
    }

    /** Why the membership counts: the attribute is not inherited, but SET ROLE reaches it, and with it every tenant. */
    @Test
    void aMemberOfASuperuserCanBecomeOne() throws SQLException {
        try (var connection = LedgerDatabase.as(MEMBER_OF_SUPERUSER_ROLE, MEMBER_OF_SUPERUSER_PASSWORD);
                var statement = connection.createStatement()) {
            assertThat(isSuperuser(statement)).isFalse();

            statement.execute("SET ROLE " + LedgerDatabase.superuserName());

            assertThat(isSuperuser(statement)).isTrue();
        }
    }

    private static boolean isSuperuser(Statement statement) throws SQLException {
        try (var rows = statement.executeQuery("SHOW is_superuser")) {
            assertThat(rows.next()).isTrue();
            return "on".equals(rows.getString(1));
        }
    }

    @Test
    void theApplicationDoesNotStartAsAMemberOfARoleThatBypassesRowLevelSecurity() throws SQLException {
        assertRole(MEMBER_OF_BYPASS_ROLE, false, false, false);

        assertRefused(MEMBER_OF_BYPASS_ROLE, MEMBER_OF_BYPASS_PASSWORD, MEMBER_OF_BYPASS_REASON,
                MEMBER_OF_BYPASS_PASSWORD);
    }

    @Test
    void theApplicationDoesNotStartAsTheOwnerOfATableOutsideThePublicSchema() throws SQLException {
        assertRole(OWNER_ELSEWHERE_ROLE, false, false, false);

        assertRefused(OWNER_ELSEWHERE_ROLE, OWNER_ELSEWHERE_PASSWORD, OWNER_REASON, OWNER_ELSEWHERE_PASSWORD);
    }

    /** A temporary table belongs to the session that made it and says nothing about the tables of the ledger. */
    @Test
    void theApplicationStartsWhileItsRoleHoldsATemporaryTable() throws SQLException {
        try (var connection = LedgerDatabase.asApplication(); var statement = connection.createStatement()) {
            statement.execute("CREATE TEMPORARY TABLE held (id integer)");
            try (var rows = statement.executeQuery("""
                    SELECT pg_get_userbyid(relowner) FROM pg_class WHERE oid = 'pg_temp.held'::regclass
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("ledger_app");
            }

            LedgerDatabase.startApplicationAs("ledger_app", "test-only-app").close();
        }
    }

    @Test
    void theApplicationDoesNotStartAsTheOwnerOfASchemaThatHoldsATable() throws SQLException {
        assertRole(SCHEMA_OWNER_ROLE, false, false, false);

        assertRefused(SCHEMA_OWNER_ROLE, SCHEMA_OWNER_PASSWORD, SCHEMA_OWNER_REASON, SCHEMA_OWNER_PASSWORD);
    }

    @Test
    void theApplicationDoesNotStartAsAMemberOfTheOwnerOfASchemaThatHoldsATable() throws SQLException {
        assertRole(MEMBER_OF_SCHEMA_OWNER_ROLE, false, false, false);

        assertRefused(MEMBER_OF_SCHEMA_OWNER_ROLE, MEMBER_OF_SCHEMA_OWNER_PASSWORD, SCHEMA_OWNER_REASON,
                MEMBER_OF_SCHEMA_OWNER_PASSWORD);
    }

    /** Why the schema counts: its owner replaces a table it does not own, and the new table comes without the policy. */
    @Test
    void theOwnerOfASchemaCanDropATableItDoesNotOwn() throws SQLException {
        try (var connection = LedgerDatabase.as(SCHEMA_OWNER_ROLE, SCHEMA_OWNER_PASSWORD);
                var statement = connection.createStatement()) {
            connection.setAutoCommit(false);

            statement.execute("DROP TABLE %s.held".formatted(OWNED_SCHEMA));
            statement.execute("CREATE TABLE %s.held (id integer)".formatted(OWNED_SCHEMA));

            // The rollback restores the table for every other test.
            connection.rollback();
        }
    }

    @Test
    void theApplicationStartsAsTheOwnerOfASchemaWithoutTables() {
        LedgerDatabase.startApplicationAs(EMPTY_SCHEMA_OWNER_ROLE, EMPTY_SCHEMA_OWNER_PASSWORD).close();
    }

    /** The public schema belongs to pg_database_owner, whose only member is whoever owns the database. */
    @Test
    void theApplicationDoesNotStartAsTheOwnerOfTheDatabase() {
        var url = LedgerDatabase.urlOf(OWNED_DATABASE);

        assertRefused(() -> LedgerDatabase.startApplicationAs(url, DATABASE_OWNER_ROLE, DATABASE_OWNER_PASSWORD),
                DATABASE_OWNER_ROLE, SCHEMA_OWNER_REASON, DATABASE_OWNER_PASSWORD);
    }

    @Test
    void theApplicationStartsWithItsOwnRoleOnADatabaseAnotherRoleOwns() {
        LedgerDatabase.startApplicationAs(LedgerDatabase.urlOf(OWNED_DATABASE), "ledger_app", "test-only-app").close();
    }
}
