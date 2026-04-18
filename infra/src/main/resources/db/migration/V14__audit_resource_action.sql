-- V14__audit_resource_action.sql
-- Replace single `type` column with `resource` + `action` for richer, extensible audit semantics.
-- resource: the entity being acted upon (USER, ORDER, PRODUCT, etc.)
-- action:   the operation performed  (CREATE, LOGIN, PAY, etc.)

ALTER TABLE audits ADD COLUMN resource VARCHAR(50);
ALTER TABLE audits ADD COLUMN action   VARCHAR(50);

UPDATE audits SET
    resource = CASE type
        WHEN 'USER_REGISTERED' THEN 'USER'
        WHEN 'LOGIN_SUCCESS'   THEN 'USER'
        WHEN 'LOGIN_FAILED'    THEN 'USER'
        WHEN 'ACCOUNT_LOCKED'  THEN 'USER'
        WHEN 'ROLE_ASSIGNED'   THEN 'ROLE'
        WHEN 'ROLE_REVOKED'    THEN 'ROLE'
        ELSE 'SYSTEM'
    END,
    action = CASE type
        WHEN 'USER_REGISTERED' THEN 'CREATE'
        WHEN 'LOGIN_SUCCESS'   THEN 'LOGIN'
        WHEN 'LOGIN_FAILED'    THEN 'LOGIN'
        WHEN 'ACCOUNT_LOCKED'  THEN 'UPDATE'
        WHEN 'ROLE_ASSIGNED'   THEN 'ASSIGN'
        WHEN 'ROLE_REVOKED'    THEN 'REVOKE'
        ELSE 'CREATE'
    END;

ALTER TABLE audits ALTER COLUMN resource SET NOT NULL;
ALTER TABLE audits ALTER COLUMN action   SET NOT NULL;
ALTER TABLE audits DROP COLUMN type;

-- Re-create the filter index on the new columns (replaces idx_audits_type, which is
-- automatically dropped when the `type` column is dropped).
CREATE INDEX idx_audits_resource ON audits(resource);
CREATE INDEX idx_audits_action   ON audits(action);
