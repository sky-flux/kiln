package com.skyflux.kiln.audit.repo;

import com.skyflux.kiln.audit.domain.AuditEvent;
import com.skyflux.kiln.audit.domain.AuditEventType;
import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;

import java.util.UUID;

/**
 * Persistence port for {@link AuditEvent} rows in the {@code audit_events}
 * table. Kept as an interface so unit tests of {@code AuditServiceImpl} can
 * substitute a fake, while {@link AuditEventJooqRepositoryImpl} handles the
 * real jOOQ wiring.
 */
public interface AuditEventJooqRepository {

    /** Inserts one row. Fails fast if the {@code id} already exists. */
    void save(AuditEvent event);

    /**
     * Returns the page of rows matching the filters, ordered
     * {@code occurred_at DESC, id DESC}.
     */
    PageResult<AuditEvent> list(PageQuery page, AuditEventType type, UUID actorUserId, UUID targetUserId);

    /** Returns the total row count matching the filters (independent of pagination). */
    long count(AuditEventType type, UUID actorUserId, UUID targetUserId);
}
