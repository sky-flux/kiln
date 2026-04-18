package com.skyflux.kiln.user.domain.model;

import com.skyflux.kiln.common.util.Ids;
import java.util.Objects;
import java.util.UUID;

/** Strongly-typed identifier for the {@link User} aggregate. */
public record UserId(UUID value) {
    public UserId {
        Objects.requireNonNull(value, "value");
    }

    public static UserId newId() {
        return new UserId(Ids.next());
    }

    public static UserId of(String s) {
        return new UserId(UUID.fromString(s));
    }
}
