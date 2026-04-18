package com.skyflux.kiln.audit.repo;

import com.skyflux.kiln.audit.domain.Audit;
import com.skyflux.kiln.audit.domain.AuditAction;
import com.skyflux.kiln.audit.domain.AuditResource;
import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;

import java.util.UUID;

/**
 * Persistence port for {@link Audit} rows in the {@code audits} table.
 * Kept as an interface so unit tests of {@code AuditServiceImpl} can
 * substitute a fake, while {@link AuditJooqRepositoryImpl} handles the
 * real jOOQ wiring.
 */
public interface AuditRepository {

    /** Inserts one row. Fails fast if the {@code id} already exists. */
    void save(Audit audit);

    /**
     * Returns the page of rows matching the filters, ordered
     * {@code occurred_at DESC, id DESC}.
     *
     * <p>A {@code null} filter argument means "no filter on that field".
     */
    PageResult<Audit> list(PageQuery page, AuditResource resource, AuditAction action,
                           UUID actorUserId, UUID targetUserId);

    /** Returns the total row count matching the filters (independent of pagination). */
    long count(AuditResource resource, AuditAction action, UUID actorUserId, UUID targetUserId);
}
