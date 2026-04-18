package com.skyflux.kiln.auth.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Role value object — mirrors a row of the {@code roles} catalogue table.
 *
 * <p>{@code code} is the stable string identifier used by Sa-Token's
 * {@code @SaCheckRole} (e.g. {@code ADMIN}). {@code name} is the
 * human-friendly label. Both are required; a blank {@code code} is rejected
 * because it would silently match every {@code @SaCheckRole("")} annotation.
 */
public record Role(UUID id, String code, String name, UUID tenantId) {
    public Role {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(tenantId, "tenantId");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code blank");
        }
    }
}
