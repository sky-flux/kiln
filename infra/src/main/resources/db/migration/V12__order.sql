CREATE TABLE orders (
    id              UUID          PRIMARY KEY,
    tenant_id       UUID          NOT NULL REFERENCES tenants(id),
    user_id         UUID          NOT NULL REFERENCES users(id),
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    total_amount    NUMERIC(19,4) NOT NULL CHECK (total_amount >= 0),
    total_currency  VARCHAR(3)    NOT NULL DEFAULT 'CNY',
    note            TEXT,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE orders IS 'Order aggregate. Status: PENDING→CONFIRMED→SHIPPED→DELIVERED|CANCELLED.';

CREATE TABLE order_items (
    id              UUID          PRIMARY KEY,
    order_id        UUID          NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id      UUID          NOT NULL,
    product_code    VARCHAR(100)  NOT NULL,
    product_name    VARCHAR(200)  NOT NULL,
    unit_price      NUMERIC(19,4) NOT NULL CHECK (unit_price >= 0),
    currency        VARCHAR(3)    NOT NULL DEFAULT 'CNY',
    quantity        INTEGER       NOT NULL CHECK (quantity >= 1),
    subtotal        NUMERIC(19,4) NOT NULL CHECK (subtotal >= 0)
);
COMMENT ON COLUMN order_items.product_code IS 'Snapshot at creation — survives product rename.';
