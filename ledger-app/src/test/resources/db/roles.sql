-- Run by whoever administers the database, before the first migration. The passwords exist only for local tests.
CREATE ROLE ledger_migrator LOGIN PASSWORD 'test-only-migrator' NOSUPERUSER NOBYPASSRLS NOCREATEROLE NOCREATEDB;
CREATE ROLE ledger_app LOGIN PASSWORD 'test-only-app' NOSUPERUSER NOBYPASSRLS NOCREATEROLE NOCREATEDB;

-- Since PostgreSQL 15 the public schema grants CREATE to its owner only.
GRANT USAGE, CREATE ON SCHEMA public TO ledger_migrator;
