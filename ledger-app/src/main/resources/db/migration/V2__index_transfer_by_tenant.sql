-- Row level security adds the tenant to every query, so walking a tenant's rows by identifier needs both columns in the
-- index, or a small tenant pays for every row of the others.
CREATE INDEX transfer_tenant_id_id_idx ON transfer (tenant_id, id DESC);
