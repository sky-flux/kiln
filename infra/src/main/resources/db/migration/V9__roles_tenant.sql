-- V9__roles_tenant.sql
-- Adds tenant ownership to the roles catalogue.
-- RLS policies and the backfill UPDATE are in R__rls.sql (PG-specific, excluded from jOOQ DDLDatabase).
-- Note: plain ADD COLUMN (no IF NOT EXISTS) — H2's DDLDatabase can't parse IF NOT EXISTS here.

ALTER TABLE roles ADD COLUMN tenant_id UUID REFERENCES tenants(id);

-- Role code is unique within a tenant.
ALTER TABLE roles DROP CONSTRAINT IF EXISTS roles_code_key;
ALTER TABLE roles ADD CONSTRAINT roles_code_tenant_unique UNIQUE (code, tenant_id);

COMMENT ON COLUMN roles.tenant_id IS 'Owning tenant. System roles belong to system tenant.';
