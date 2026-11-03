package com.carloscuatin.ledgerapp.ledger.adapters.out.persistence;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Stops the startup when the application is connected as a role that can get past row level security. Such a role would
 * raise no error and would appear to work, while every tenant saw the rows of every other. It runs while the context is
 * being built, so a refused process never gets to do any work.
 * <p>
 * Being a member of a role counts as being that role, inherited or not, because SET ROLE reaches it either way. Besides
 * the attributes that skip the policies, ownership counts: the owner of a table can lift its policy, and the owner of a
 * schema can drop a table it holds and recreate it without one. Ownership is looked up in every schema except the
 * system ones, which hold no tenant data; those include the temporary schemas, where any role can own a table for as
 * long as its session lasts.
 */
final class DatabaseRoleGuard implements InitializingBean {

    private static final String THIS_ROLE = """
            SELECT r.rolname, r.rolsuper, r.rolbypassrls,
                   EXISTS (SELECT 1 FROM pg_roles g
                           WHERE g.rolsuper AND pg_has_role(r.oid, g.oid, 'MEMBER'))
                   AS member_of_a_superuser,
                   EXISTS (SELECT 1 FROM pg_roles g
                           WHERE g.rolbypassrls AND pg_has_role(r.oid, g.oid, 'MEMBER'))
                   AS member_of_a_bypassing_role,
                   EXISTS (SELECT 1
                           FROM pg_class c
                           JOIN pg_namespace n ON n.oid = c.relnamespace
                           WHERE n.nspname !~ '^pg_' AND n.nspname <> 'information_schema'
                             AND c.relkind IN ('r', 'p') AND pg_has_role(r.oid, c.relowner, 'MEMBER'))
                   AS acts_as_a_table_owner,
                   EXISTS (SELECT 1
                           FROM pg_class c
                           JOIN pg_namespace n ON n.oid = c.relnamespace
                           WHERE n.nspname !~ '^pg_' AND n.nspname <> 'information_schema'
                             AND c.relkind IN ('r', 'p') AND pg_has_role(r.oid, n.nspowner, 'MEMBER'))
                   AS acts_as_the_owner_of_a_schema_with_tables
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
                        rows.getBoolean("rolbypassrls"), rows.getBoolean("member_of_a_superuser"),
                        rows.getBoolean("member_of_a_bypassing_role"), rows.getBoolean("acts_as_a_table_owner"),
                        rows.getBoolean("acts_as_the_owner_of_a_schema_with_tables")))
                .single();
        refuseIf(role.superuser(), role.name(), "it is a superuser");
        refuseIf(role.bypassesRowLevelSecurity(), role.name(), "it has BYPASSRLS");
        refuseIf(role.memberOfASuperuser(), role.name(), "it is a member of a superuser");
        refuseIf(role.memberOfABypassingRole(), role.name(), "it is a member of a role with BYPASSRLS");
        refuseIf(role.actsAsATableOwner(), role.name(), "it owns, or is a member of the owner of, a table");
        refuseIf(role.actsAsTheOwnerOfASchemaWithTables(), role.name(),
                "it owns, or is a member of the owner of, a schema that holds a table");
    }

    private static void refuseIf(boolean condition, String role, String reason) {
        if (condition) {
            throw new IllegalStateException("Refusing to start with database role '%s': %s".formatted(role, reason));
        }
    }

    private record Role(String name, boolean superuser, boolean bypassesRowLevelSecurity, boolean memberOfASuperuser,
            boolean memberOfABypassingRole, boolean actsAsATableOwner, boolean actsAsTheOwnerOfASchemaWithTables) {
    }
}
