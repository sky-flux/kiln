package com.skyflux.kiln.user.domain.event;

import com.skyflux.kiln.user.domain.model.UserId;

import java.time.Instant;
import java.util.UUID;

/** Domain event fired when a new {@code User} is registered. */
public record UserRegistered(UserId userId, UUID tenantId, String email, Instant occurredAt) {
    public static UserRegistered of(UserId userId, UUID tenantId, String email) {
        return new UserRegistered(userId, tenantId, email, Instant.now());
    }
}
