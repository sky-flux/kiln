package com.skyflux.kiln.common.util;

import com.github.f4b6a3.uuid.UuidCreator;
import java.util.UUID;

/** Factory for time-ordered UUID v7 identifiers. Use instead of {@code UUID.randomUUID()}. */
public final class Ids {
    private Ids() {}

    public static UUID next() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}
