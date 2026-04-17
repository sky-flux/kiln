-- Phase 4.2: Role-Based Access Control data source.
--
-- Tables:
--   roles              — system-wide role catalogue (ADMIN, USER, …)
--   permissions        — fine-grained permission catalogue (user.admin, …)
--   role_permissions   — many-to-many: which permissions a role grants
--   user_roles         — many-to-many: which roles a user holds
--
-- Seed: two default roles (ADMIN / USER), two baseline permissions, and
-- role ↔ permission wiring. No seed users — integration tests assign ADMIN
-- directly via the auth-module repository when they need it.

CREATE TABLE roles (
    id          UUID PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE  roles      IS 'RBAC role catalogue.';
COMMENT ON COLUMN roles.code IS 'Stable uppercase identifier used by @SaCheckRole.';

CREATE TABLE permissions (
    id          UUID PRIMARY KEY,
    code        VARCHAR(100) NOT NULL UNIQUE,
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE  permissions      IS 'RBAC permission catalogue (fine-grained capabilities).';
COMMENT ON COLUMN permissions.code IS 'Dotted identifier used by @SaCheckPermission, e.g. user.admin.';

-- ON DELETE RESTRICT on role_id / permission_id: deleting a seeded role or
-- permission should FAIL loudly rather than silently cascade-delete every
-- assignment. A real delete path (Phase 5+) will require an explicit
-- migration that first detaches the dependants.
CREATE TABLE role_permissions (
    role_id       UUID NOT NULL REFERENCES roles(id)       ON DELETE RESTRICT,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE RESTRICT,
    PRIMARY KEY (role_id, permission_id)
);
COMMENT ON TABLE role_permissions IS 'Role → Permission many-to-many.';

-- user_roles.user_id DOES cascade — deleting a user should remove their
-- assignments. Keeping role_id on RESTRICT for the same reason as above.
CREATE TABLE user_roles (
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id     UUID        NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, role_id)
);
COMMENT ON TABLE user_roles IS 'User → Role many-to-many. assigned_at auditable.';

-- Indexes on the "right-column" FKs so Sa-Token's per-request role / permission
-- lookup (StpInterfaceImpl) doesn't seq-scan under load. The composite PRIMARY
-- KEYs already cover leftmost-prefix queries (user_roles by user_id,
-- role_permissions by role_id), so only the right-column indexes are needed.
CREATE INDEX idx_user_roles_role_id             ON user_roles(role_id);
CREATE INDEX idx_role_permissions_permission_id ON role_permissions(permission_id);

-- ────────── Seed data ──────────
--
-- Guard idempotency with WHERE NOT EXISTS rather than PG-specific
-- ON CONFLICT DO NOTHING: the latter is parsed as H2's MERGE INTO by jOOQ's
-- DDLDatabase codegen and trips an "Ambiguous column name id" translation
-- bug. WHERE NOT EXISTS is accepted by both H2 and PostgreSQL and is still
-- re-runnable on a partially populated schema (a cheap insurance — Flyway's
-- checksum also guarantees V4 runs exactly once).

INSERT INTO roles (id, code, name, description)
SELECT v.id, v.code, v.name, v.description
FROM (VALUES
    ('00000000-0000-0000-0000-000000000001'::uuid, 'ADMIN', 'Administrator',  'Full administrative access.'),
    ('00000000-0000-0000-0000-000000000002'::uuid, 'USER',  'Regular user',   'Default role assigned on registration.')
) AS v(id, code, name, description)
WHERE NOT EXISTS (SELECT 1 FROM roles r WHERE r.id = v.id);

INSERT INTO permissions (id, code, name, description)
SELECT v.id, v.code, v.name, v.description
FROM (VALUES
    ('10000000-0000-0000-0000-000000000001'::uuid, 'user.admin', 'Administer users', 'List / delete / promote any user.'),
    ('10000000-0000-0000-0000-000000000002'::uuid, 'user.read',  'Read own user',    'Read information about the logged-in user.')
) AS v(id, code, name, description)
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.id = v.id);

-- ADMIN: all permissions. USER: self-read only.
INSERT INTO role_permissions (role_id, permission_id)
SELECT v.role_id, v.permission_id
FROM (VALUES
    ('00000000-0000-0000-0000-000000000001'::uuid, '10000000-0000-0000-0000-000000000001'::uuid),
    ('00000000-0000-0000-0000-000000000001'::uuid, '10000000-0000-0000-0000-000000000002'::uuid),
    ('00000000-0000-0000-0000-000000000002'::uuid, '10000000-0000-0000-0000-000000000002'::uuid)
) AS v(role_id, permission_id)
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions rp
     WHERE rp.role_id = v.role_id AND rp.permission_id = v.permission_id);
