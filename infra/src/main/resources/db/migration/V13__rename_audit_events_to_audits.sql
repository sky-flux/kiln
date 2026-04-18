-- V13__rename_audit_events_to_audits.sql
-- Rename audit_events table and its indexes to match the module naming convention.
-- audit_events → audits  (consistent with users, roles, tenants, products, members, orders)

ALTER TABLE audit_events RENAME TO audits;

ALTER INDEX idx_audit_events_occurred_at RENAME TO idx_audits_occurred_at;
ALTER INDEX idx_audit_events_actor       RENAME TO idx_audits_actor;
ALTER INDEX idx_audit_events_type        RENAME TO idx_audits_type;
