package com.skyflux.kiln.audit.api;

import com.skyflux.kiln.audit.domain.Audit;
import com.skyflux.kiln.audit.domain.AuditType;
import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;

import java.util.UUID;

/**
 * Read-only query surface over {@code audit_events}. Used by the admin audit
 * query endpoint (and, downstream, by compliance exports).
 *
 * <p>Ordering is fixed — {@code occurred_at DESC, id DESC} — so pagination is
 * stable even when two rows share a millisecond.
 *
 * <p>A {@code null} filter argument is semantically "no filter on that field",
 * NOT "match only rows where the column IS NULL". The controller surfaces this
 * via optional query parameters.
 */
public interface AuditQueryService {

    /**
     * Paginated list filtered by optional type, actor, and/or target.
     *
     * @param page          pagination window (required; non-null)
     * @param type          optional audit-type filter; {@code null} = match any type
     * @param actorUserId   optional actor filter; {@code null} = match any actor
     * @param targetUserId  optional target filter; {@code null} = match any target.
     *                      Gate 3 M2: compliance use-cases ask "everything that
     *                      happened to user X" — including login failures which
     *                      legitimately have null actor but non-null target.
     */
    PageResult<Audit> list(PageQuery page, AuditType type, UUID actorUserId, UUID targetUserId);
}
