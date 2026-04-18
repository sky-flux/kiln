package com.skyflux.kiln.audit.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record of a single audit-log row.
 *
 * <p>Mirrors a row of the {@code audits} table (V14 Flyway migration).
 * Kept in the {@code domain} package — zero framework imports — so listeners
 * can assemble events from cross-module domain events before handing them to
 * the repository.
 *
 * <p>Nullable fields reflect real-world event shapes:
 * <ul>
 *   <li>{@code actorUserId} — null for pre-auth events (e.g. login failures
 *       with an unknown username) or system-originated actions.</li>
 *   <li>{@code targetUserId} — null for events with no distinct target.</li>
 *   <li>{@code details} — optional JSON payload for structured metadata.
 *       Null when no extra context is available.</li>
 *   <li>{@code requestId} — optional MDC {@code X-Request-Id} correlation
 *       key. Null for events produced outside an HTTP request (async jobs).</li>
 * </ul>
 *
 * <p>{@code id}, {@code occurredAt}, {@code resource}, and {@code action} are
 * mandatory: a row with no primary key, no timestamp, or no resource/action tag
 * is not recoverable as an audit record.
 */
public record Audit(
        UUID id,
        Instant occurredAt,
        AuditResource resource,
        AuditAction action,
        UUID actorUserId,
        UUID targetUserId,
        String details,
        String requestId
) {

    public Audit {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(action, "action");
    }

    /**
     * Factory that generates a fresh {@link UUID} and stamps {@code occurredAt}
     * from the supplied {@link Clock}. The clock is injected rather than using
     * {@link Instant#now()} so tests and deterministic replay can control event
     * timestamps exactly.
     *
     * @param clock        time source; {@code Clock.systemUTC()} in production wiring
     * @param resource     entity being acted upon (required)
     * @param action       operation performed (required)
     * @param actorUserId  who performed the action, nullable
     * @param targetUserId whom the action targeted, nullable
     * @param details      free-form JSON payload, nullable
     * @param requestId    MDC correlation key, nullable
     * @return a new event with {@code id = UUID.randomUUID()} and
     *         {@code occurredAt = clock.instant()}
     */
    public static Audit create(
            Clock clock,
            AuditResource resource,
            AuditAction action,
            UUID actorUserId,
            UUID targetUserId,
            String details,
            String requestId) {
        Objects.requireNonNull(clock, "clock");
        return new Audit(
                UUID.randomUUID(),
                clock.instant(),
                resource,
                action,
                actorUserId,
                targetUserId,
                details,
                requestId);
    }
}
