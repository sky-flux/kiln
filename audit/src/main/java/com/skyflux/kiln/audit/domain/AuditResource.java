package com.skyflux.kiln.audit.domain;

/**
 * The entity or resource category being acted upon.
 * Stored as the raw enum name in {@code audits.resource}.
 */
public enum AuditResource {
    USER, TENANT, ROLE, ORDER, PRODUCT, MEMBER, SYSTEM
}
