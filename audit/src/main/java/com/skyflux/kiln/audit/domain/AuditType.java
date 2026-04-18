package com.skyflux.kiln.audit.domain;

/**
 * Closed catalogue of audit event categories. Stored as the raw enum name in
 * {@code audits.type} (see V13 Flyway migration). Widen this enum only by
 * <em>adding</em> new constants — renaming or removing a value is a breaking
 * change because historical rows still reference the old name.
 */
public enum AuditType {
    USER_REGISTERED,
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    ROLE_ASSIGNED,
    ROLE_REVOKED,
    ACCOUNT_LOCKED
}
