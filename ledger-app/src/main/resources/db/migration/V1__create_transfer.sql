-- The roles exist before this runs: whoever administers the database creates them, and the role that runs this
-- migration ends up owning what it creates.

CREATE FUNCTION current_tenant_id() RETURNS uuid
    LANGUAGE sql STABLE
    AS $$ SELECT current_setting('app.tenant_id', true)::uuid $$;

CREATE TABLE transfer (
    id                     uuid PRIMARY KEY,
    tenant_id              uuid NOT NULL DEFAULT current_tenant_id(),
    source_account_id      uuid NOT NULL,
    destination_account_id uuid NOT NULL,
    amount_minor           bigint NOT NULL CHECK (amount_minor > 0),
    currency               char(3) NOT NULL,
    status                 text NOT NULL CHECK (status IN ('PENDING', 'POSTED', 'VOIDED', 'EXPIRED')),
    CHECK (source_account_id <> destination_account_id)
);

ALTER TABLE transfer ENABLE ROW LEVEL SECURITY;
-- Without FORCE, the owner of a table is exempt from its policies.
ALTER TABLE transfer FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON transfer
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

GRANT SELECT, INSERT ON transfer TO ledger_app;
