package com.skyflux.kiln.auth.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Permission value object — mirrors a row of the {@code permissions}
 * catalogue table.
 *
 * <p>{@code code} is the dotted identifier used by
 * {@code @SaCheckPermission} (e.g. {@code user.admin}). {@code name} is the
 * human-friendly label.
 */
public record Permission(UUID id, String code, String name) {
    public Permission {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
    }
}
