CREATE TABLE members (
    id          UUID        PRIMARY KEY,
    tenant_id   UUID        NOT NULL REFERENCES tenants(id),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    level       VARCHAR(20) NOT NULL DEFAULT 'BRONZE',
    points      INTEGER     NOT NULL DEFAULT 0 CHECK (points >= 0),
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    joined_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT members_user_tenant_unique UNIQUE (user_id, tenant_id)
);
COMMENT ON TABLE members IS 'Loyalty membership profile. One per user per tenant.';
