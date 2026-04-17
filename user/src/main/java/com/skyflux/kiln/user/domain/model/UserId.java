package com.skyflux.kiln.user.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Strongly-typed identifier for the {@link User} aggregate. */
public record UserId(UUID value) {
    public UserId {
        Objects.requireNonNull(value, "value");
    }

    public static UserId newId() {
        return new UserId(UUID.randomUUID());
    }

    public static UserId of(String s) {
        return new UserId(UUID.fromString(s));
    }
}
