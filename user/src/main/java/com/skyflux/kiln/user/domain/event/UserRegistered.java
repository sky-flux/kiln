package com.skyflux.kiln.user.domain.event;

import com.skyflux.kiln.user.domain.model.UserId;

import java.time.Instant;

/** Domain event fired when a new {@code User} is registered. */
public record UserRegistered(UserId userId, String email, Instant occurredAt) {
    public static UserRegistered of(UserId userId, String email) {
        return new UserRegistered(userId, email, Instant.now());
    }
}
