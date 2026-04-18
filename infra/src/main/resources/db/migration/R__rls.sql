-- R__rls.sql
-- PostgreSQL Row-Level Security policies.
-- Flyway applies this after V__ migrations. jOOQ DDLDatabase does NOT read this file
-- (H2 can't parse ENABLE ROW LEVEL SECURITY / CREATE POLICY).
-- SET LOCAL app.tenant_id is set by TenantRlsListener before each jOOQ statement.

-- tenants: NO RLS — filter reads tenants by code before session exists.

-- Seed the system tenant with a fixed UUID so migrations are idempotent.
-- ON CONFLICT DO NOTHING is PG-specific; placed here to exclude from jOOQ DDLDatabase.
INSERT INTO tenants (id, code, name, status)
VALUES ('00000000-0000-7000-8000-000000000001', 'system', 'System Tenant', 'ACTIVE')
ON CONFLICT DO NOTHING;

-- Back-fill existing users to system tenant (safe to re-run via R__ idempotency).
UPDATE users SET tenant_id = '00000000-0000-7000-8000-000000000001' WHERE tenant_id IS NULL;

-- Enforce NOT NULL after backfill (idempotent: re-setting NOT NULL when already NOT NULL is a no-op in PG).
ALTER TABLE users ALTER COLUMN tenant_id SET NOT NULL;

-- users: isolate by tenant_id.
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE users FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON users;
CREATE POLICY tenant_isolation ON users
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- Back-fill existing roles to system tenant (safe to re-run via R__ idempotency).
UPDATE roles SET tenant_id = '00000000-0000-7000-8000-000000000001' WHERE tenant_id IS NULL;

-- Enforce NOT NULL after backfill (idempotent in PG).
ALTER TABLE roles ALTER COLUMN tenant_id SET NOT NULL;

-- roles: isolate by tenant_id.
ALTER TABLE roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE roles FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON roles;
CREATE POLICY tenant_isolation ON roles
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE members ENABLE ROW LEVEL SECURITY;
ALTER TABLE members FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON members;
CREATE POLICY tenant_isolation ON members
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE products ENABLE ROW LEVEL SECURITY;
ALTER TABLE products FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON products;
CREATE POLICY tenant_isolation ON products
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE orders FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON orders;
CREATE POLICY tenant_isolation ON orders
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
