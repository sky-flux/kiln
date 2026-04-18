package com.skyflux.kiln.audit.domain;

/**
 * The operation performed on a {@link AuditResource}.
 * Stored as the raw enum name in {@code audits.action}.
 */
public enum AuditAction {
    CREATE, READ, UPDATE, DELETE,
    LOGIN, LOGOUT,
    PAY, CANCEL,
    ASSIGN, REVOKE,
    AWARD_POINTS
}
