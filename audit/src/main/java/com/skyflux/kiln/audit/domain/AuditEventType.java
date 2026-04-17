package com.skyflux.kiln.audit.domain;

/**
 * Closed catalogue of audit-event categories. Stored as the raw enum name in
 * {@code audit_events.type} (see V5 Flyway migration). Widen this enum only by
 * <em>adding</em> new constants — renaming or removing a value is a breaking
 * change because historical rows still reference the old name.
 */
public enum AuditEventType {
    USER_REGISTERED,
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    ROLE_ASSIGNED,
    ROLE_REVOKED,
    ACCOUNT_LOCKED
}
