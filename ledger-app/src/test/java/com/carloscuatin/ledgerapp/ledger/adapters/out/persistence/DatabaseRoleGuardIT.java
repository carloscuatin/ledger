package com.carloscuatin.ledgerapp.ledger.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;

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

    private static final String OWNER_REASON = "it owns, or is a member of the owner of, a table in the public schema";

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
        }
    }

    /** Roles belong to the whole server, which other test classes share. */
    @AfterAll
    static void theRolesUnderTestAreGone() throws SQLException {
        try (var connection = LedgerDatabase.asSuperuser(); var statement = connection.createStatement()) {
            statement.execute("DROP ROLE %s, %s, %s, %s"
                    .formatted(BYPASS_ROLE, MEMBER_ROLE, NOINHERIT_ROLE, UNRELATED_ROLE));
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
        assertThatThrownBy(() -> LedgerDatabase.startApplicationAs(role, password))
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
}
