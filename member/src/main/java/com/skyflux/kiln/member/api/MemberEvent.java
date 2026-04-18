package com.skyflux.kiln.member.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Public domain events published by the member module.
 * Placed in member.api so other modules (audit) can listen
 * without crossing into member.internal.
 */
public sealed interface MemberEvent {
    record PointsAwarded(UUID userId, UUID tenantId, int points, Instant occurredAt)
        implements MemberEvent {}
}
