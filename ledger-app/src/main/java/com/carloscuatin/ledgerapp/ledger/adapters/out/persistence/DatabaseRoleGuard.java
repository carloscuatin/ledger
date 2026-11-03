package com.carloscuatin.ledgerapp.ledger.adapters.out.persistence;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Stops the startup when the application is connected as a role that row level security does not apply to. Such a role
 * would raise no error and would appear to work, while every tenant saw the rows of every other. It runs while the
 * context is being built, so a refused process never gets to do any work.
 * A member of the owner counts even if it does not inherit, because it can still become the owner with SET ROLE.
 */
final class DatabaseRoleGuard implements InitializingBean {

    private static final String THIS_ROLE = """
            SELECT r.rolname, r.rolsuper, r.rolbypassrls,
                   EXISTS (SELECT 1
                           FROM pg_class c
                           JOIN pg_namespace n ON n.oid = c.relnamespace
                           WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p') AND pg_has_role(r.oid, c.relowner, 'MEMBER'))
                   AS acts_as_a_table_owner
            FROM pg_roles r
            WHERE r.rolname = current_user
            """;

    private final JdbcClient jdbc;

    DatabaseRoleGuard(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void afterPropertiesSet() {
        // single() fails when the query finds no row, so a role that cannot be checked does not start either.
        var role = jdbc.sql(THIS_ROLE)
                .query((rows, number) -> new Role(rows.getString("rolname"), rows.getBoolean("rolsuper"),
                        rows.getBoolean("rolbypassrls"), rows.getBoolean("acts_as_a_table_owner")))
                .single();
        refuseIf(role.superuser(), role.name(), "it is a superuser");
        refuseIf(role.bypassesRowLevelSecurity(), role.name(), "it has BYPASSRLS");
        refuseIf(role.actsAsATableOwner(), role.name(),
                "it owns, or is a member of the owner of, a table in the public schema");
    }

    private static void refuseIf(boolean condition, String role, String reason) {
        if (condition) {
            throw new IllegalStateException("Refusing to start with database role '%s': %s".formatted(role, reason));
        }
    }

    private record Role(String name, boolean superuser, boolean bypassesRowLevelSecurity, boolean actsAsATableOwner) {
    }
}
