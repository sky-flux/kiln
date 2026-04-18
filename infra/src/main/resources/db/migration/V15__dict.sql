-- V15__dict.sql
-- Two-level data dictionary: system dicts (tenant_id IS NULL) shared by all;
-- tenant dicts (tenant_id NOT NULL) scoped to the owning tenant.

CREATE TABLE dict_types (
    id         UUID         PRIMARY KEY,
    code       VARCHAR(50)  NOT NULL UNIQUE,
    name       VARCHAR(100) NOT NULL,
    is_system  BOOLEAN      NOT NULL DEFAULT false,
    tenant_id  UUID         REFERENCES tenants(id),  -- null = system type
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE  dict_types           IS 'Data dictionary type catalogue. No RLS — readable by all.';
COMMENT ON COLUMN dict_types.is_system IS 'true = seeded by migration, read-only via API.';
COMMENT ON COLUMN dict_types.tenant_id IS 'null for system types; tenant UUID for tenant-custom types.';

CREATE TABLE dict_items (
    id         UUID         PRIMARY KEY,
    type_id    UUID         NOT NULL REFERENCES dict_types(id) ON DELETE CASCADE,
    code       VARCHAR(50)  NOT NULL,
    label      VARCHAR(200) NOT NULL,
    sort_order INTEGER      NOT NULL DEFAULT 0,
    is_active  BOOLEAN      NOT NULL DEFAULT true,
    tenant_id  UUID         REFERENCES tenants(id),  -- mirrors dict_types.tenant_id
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT dict_items_type_code_unique UNIQUE (type_id, code)
);
COMMENT ON TABLE  dict_items           IS 'Dict items. RLS: system rows (tenant_id IS NULL) visible to all.';
COMMENT ON COLUMN dict_items.sort_order IS 'Ascending sort order for dropdown display.';

CREATE INDEX idx_dict_items_type_id  ON dict_items(type_id);
CREATE INDEX idx_dict_items_tenant   ON dict_items(tenant_id);

-- NOTE: seed INSERT statements (dict_types + dict_items) use ON CONFLICT DO NOTHING
-- which H2 cannot parse. They live in R__rls.sql alongside other PG-specific DML.
