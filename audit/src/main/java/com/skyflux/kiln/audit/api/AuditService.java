package com.skyflux.kiln.audit.api;

import com.skyflux.kiln.audit.domain.Audit;
import com.skyflux.kiln.audit.domain.AuditAction;
import com.skyflux.kiln.audit.domain.AuditResource;

import java.util.UUID;

/**
 * Records a single audit event. Implementations are responsible for stamping
 * a fresh {@code id} and {@code occurredAt} (via an injected {@link java.time.Clock})
 * so callers only carry business payload.
 *
 * <p>Contract: {@code record} runs in a {@code REQUIRES_NEW} transaction so a
 * rollback in the caller's transaction does NOT erase the audit row. The audit
 * trail must survive business-logic failures — that's half the reason it exists.
 */
public interface AuditService {

    /**
     * Persist a new audit event.
     *
     * @param resource     entity being acted upon (required)
     * @param action       operation performed (required)
     * @param actorUserId  who performed the action, nullable for pre-auth / system events
     * @param targetUserId whom the action targeted, nullable if target == actor
     * @param details      free-form JSON payload, nullable
     * @param requestId    MDC correlation key, nullable
     * @return the persisted event, including the newly-generated {@code id}
     *         and {@code occurredAt}
     */
    Audit record(AuditResource resource,
                 AuditAction action,
                 UUID actorUserId,
                 UUID targetUserId,
                 String details,
                 String requestId);
}
