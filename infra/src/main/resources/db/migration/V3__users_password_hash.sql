-- Phase 4: add password_hash column to users.
--
-- Stores a self-contained Argon2id-encoded string (algorithm + params +
-- salt + digest). NOT NULL enforces "every user has a password"; empty
-- string is rejected at the application layer via PasswordService.
--
-- No backfill clause: Phase 1-3 had no persisted users (the Phase 3
-- KilnIntegrationTest uses fresh Testcontainers DBs), so ADD COLUMN
-- NOT NULL succeeds against an empty table.

ALTER TABLE users
    ADD COLUMN password_hash TEXT NOT NULL;

COMMENT ON COLUMN users.password_hash IS
    'Argon2id-encoded password string (algorithm + params + salt + digest). See common/security/PasswordService.';
