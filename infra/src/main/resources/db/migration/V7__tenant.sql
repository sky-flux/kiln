-- V7__tenant.sql
-- Creates the tenants catalogue and adds tenant_id to users.
-- RLS policies are in R__rls.sql (excluded from jOOQ DDLDatabase — H2 can't parse them).
-- Seed data (ON CONFLICT DO NOTHING is PG-specific) is also in R__rls.sql.

CREATE TABLE tenants (
    id         UUID         PRIMARY KEY,
    code       VARCHAR(50)  NOT NULL UNIQUE,
    name       VARCHAR(200) NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT tenants_code_lower CHECK (code = lower(code))
);
COMMENT ON TABLE  tenants      IS 'Tenant catalogue. No RLS — needed for pre-auth lookup by code.';
COMMENT ON COLUMN tenants.code IS 'Lowercase slug, e.g. acme-corp. Used in X-Tenant-Code header.';

-- Add tenant_id to users; back-fill to system tenant done in R__rls.sql after seed.
-- Note: plain ADD COLUMN (no IF NOT EXISTS) — H2's DDLDatabase can't parse IF NOT EXISTS here.
ALTER TABLE users ADD COLUMN tenant_id UUID REFERENCES tenants(id);

-- Email uniqueness is now per-tenant.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_key;
ALTER TABLE users ADD CONSTRAINT users_email_tenant_unique UNIQUE (email, tenant_id);
COMMENT ON COLUMN users.tenant_id IS 'Owning tenant. Inherited by all queries via PostgreSQL RLS.';
