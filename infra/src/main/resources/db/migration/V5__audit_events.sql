-- Phase 4.3 Wave 1: append-only audit log.
--
-- Intentionally NO foreign keys on actor_user_id / target_user_id —
-- the audit log must outlive the subjects it records. Deleting a user
-- must leave their audit trail intact for compliance / forensics.
-- Enforce referential correctness at write time in the application layer
-- (listener checks the user exists before stamping the event).
CREATE TABLE audit_events (
    id              UUID PRIMARY KEY,
    occurred_at     TIMESTAMPTZ NOT NULL,
    type            VARCHAR(50) NOT NULL,
    actor_user_id   UUID,
    target_user_id  UUID,
    details         JSONB,
    request_id      VARCHAR(128)
);
COMMENT ON TABLE audit_events                 IS 'Append-only audit log. No FK to users(id) so log rows survive user deletion.';
COMMENT ON COLUMN audit_events.type           IS 'Matches the AuditEventType enum value, e.g. LOGIN_FAILED.';
COMMENT ON COLUMN audit_events.actor_user_id  IS 'Who did it. NULL for system-originated or pre-auth events.';
COMMENT ON COLUMN audit_events.target_user_id IS 'Whom it was done to. NULL if event has no target (e.g. LOGIN_SUCCESS, target==actor).';
COMMENT ON COLUMN audit_events.details        IS 'Free-form JSON payload (structured metadata).';
COMMENT ON COLUMN audit_events.request_id     IS 'MDC X-Request-Id correlation key.';

-- Access patterns we design for in Wave 2+:
--   (1) "What happened in the last 24h?"  → occurred_at DESC scan.
--   (2) "Show me this user's activity."    → actor_user_id equality.
--   (3) "How many LOGIN_FAILED today?"     → type equality + occurred_at range.
-- The (occurred_at DESC) ordering is explicit so PG can do a backwards index
-- scan for the common "latest N" case without an extra sort step.
CREATE INDEX idx_audit_events_occurred_at ON audit_events(occurred_at DESC);
CREATE INDEX idx_audit_events_actor       ON audit_events(actor_user_id);
CREATE INDEX idx_audit_events_type        ON audit_events(type);
