-- V1__init_schema.sql
-- Initial Kiln schema: users table matching the User aggregate (user/domain/model/User.java).
-- Conventions:
--   * UUID primary keys
--   * TIMESTAMPTZ audit columns (Instant-compatible)
--   * VARCHAR caps on display strings; CHECK on non-blank names
--   * Case-insensitive email uniqueness: email is stored normalized (lower-cased,
--     trimmed) by the domain value object; a plain UNIQUE constraint is then
--     sufficient. This avoids a functional index, which H2 (used by jOOQ's
--     DDLDatabase during codegen) cannot parse.

CREATE TABLE users (
    id         UUID         PRIMARY KEY,
    name       VARCHAR(100) NOT NULL CHECK (length(trim(name)) > 0),
    email      VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  users             IS 'Application user aggregate root.';
COMMENT ON COLUMN users.id          IS 'Stable UUID identity assigned at creation.';
COMMENT ON COLUMN users.name        IS 'Display name; non-blank, up to 100 chars.';
COMMENT ON COLUMN users.email       IS 'Email address; stored normalized (lower-cased, trimmed) for case-insensitive uniqueness; up to 255 chars.';
COMMENT ON COLUMN users.created_at  IS 'UTC instant when the row was first inserted.';
COMMENT ON COLUMN users.updated_at  IS 'UTC instant when the row was last updated.';
