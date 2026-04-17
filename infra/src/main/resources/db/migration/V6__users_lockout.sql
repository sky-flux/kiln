-- Phase 4.3 Wave 1: lockout bookkeeping columns on users.
--
-- PASSIVE CARRIERS ONLY — schema carries the state; Wave 2 adds the lockout
-- enforcement logic (threshold checks, registerLoginFailure / registerLoginSuccess,
-- AuthenticateUserService updates). Until Wave 2 lands the columns stay at
-- their defaults (0 / NULL) and have no behavioral effect.
--
-- failed_login_attempts: monotonic counter of consecutive failed logins since
-- the last successful login; reset to 0 on LOGIN_SUCCESS.
-- locked_until: if non-null AND in the future, any authentication attempt is
-- rejected; cleared on LOGIN_SUCCESS.

ALTER TABLE users
    ADD COLUMN failed_login_attempts INT         NOT NULL DEFAULT 0,
    ADD COLUMN locked_until          TIMESTAMPTZ NULL;

COMMENT ON COLUMN users.failed_login_attempts IS 'Consecutive failed login attempts since last successful login. Resets to 0 on LOGIN_SUCCESS.';
COMMENT ON COLUMN users.locked_until          IS 'If non-null AND in the future, any authentication attempt is rejected. Set when the failed-attempts threshold trips; cleared on LOGIN_SUCCESS.';

-- Partial index supports a hypothetical "list currently locked users" admin
-- query. The partial predicate is pure column-IS-NOT-NULL (no functional
-- expression), so jOOQ's DDLDatabase H2 parser accepts it.
CREATE INDEX idx_users_locked_until ON users(locked_until) WHERE locked_until IS NOT NULL;
