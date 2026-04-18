CREATE TABLE products (
    id              UUID            PRIMARY KEY,
    tenant_id       UUID            NOT NULL REFERENCES tenants(id),
    code            VARCHAR(100)    NOT NULL,
    name            VARCHAR(200)    NOT NULL CHECK (length(trim(name)) > 0),
    description     TEXT,
    price_amount    NUMERIC(19, 4)  NOT NULL CHECK (price_amount >= 0),
    price_currency  VARCHAR(3)      NOT NULL DEFAULT 'CNY',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT products_code_tenant_unique UNIQUE (code, tenant_id)
);
COMMENT ON TABLE products IS 'Product catalogue. Tenant-scoped via RLS.';
