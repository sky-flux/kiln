package com.skyflux.kiln.audit.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record of a single audit-log row.
 *
 * <p>Mirrors a row of the {@code audits} table (V13 Flyway migration).
 * Kept in the {@code domain} package — zero framework imports — so Wave 2's
 * listener can assemble events from cross-module domain events before
 * handing them to the repository.
 *
 * <p>Nullable fields reflect real-world event shapes:
 * <ul>
 *   <li>{@code actorUserId} — null for pre-auth events (e.g. {@link
 *       AuditType#LOGIN_FAILED} with an unknown username) or
 *       system-originated actions.</li>
 *   <li>{@code targetUserId} — null for events with no distinct target
 *       (e.g. {@link AuditType#LOGIN_SUCCESS}, target == actor by
 *       convention).</li>
 *   <li>{@code details} — optional JSON payload for structured metadata
 *       (e.g. failed-login reason, assigned role code). Null when no extra
 *       context is available.</li>
 *   <li>{@code requestId} — optional MDC {@code X-Request-Id} correlation
 *       key. Null for events produced outside an HTTP request (async jobs).</li>
 * </ul>
 *
 * <p>{@code id}, {@code occurredAt}, and {@code type} are mandatory: a row
 * with no primary key, no timestamp, or no type tag is not recoverable as an
 * audit record.
 */
public record Audit(
        UUID id,
        Instant occurredAt,
        AuditType type,
        UUID actorUserId,
        UUID targetUserId,
        String details,
        String requestId
) {

    public Audit {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(type, "type");
    }

    /**
     * Factory that generates a fresh {@link UUID} and stamps {@code occurredAt}
     * from the supplied {@link Clock}. The clock is injected rather than using
     * {@link Instant#now()} so tests and deterministic replay (e.g. Flowable
     * workflow audit playback) can control event timestamps exactly.
     *
     * @param clock        time source; {@code Clock.systemUTC()} in production wiring
     * @param type         event category (required)
     * @param actorUserId  who performed the action, nullable
     * @param targetUserId whom the action targeted, nullable
     * @param details      free-form JSON payload, nullable
     * @param requestId    MDC correlation key, nullable
     * @return a new event with {@code id = UUID.randomUUID()} and
     *         {@code occurredAt = clock.instant()}
     */
    public static Audit create(
            Clock clock,
            AuditType type,
            UUID actorUserId,
            UUID targetUserId,
            String details,
            String requestId) {
        Objects.requireNonNull(clock, "clock");
        return new Audit(
                UUID.randomUUID(),
                clock.instant(),
                type,
                actorUserId,
                targetUserId,
                details,
                requestId);
    }
}
