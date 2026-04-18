package com.skyflux.kiln.tenant.api;

import com.skyflux.kiln.common.util.Ids;
import java.util.Objects;
import java.util.UUID;

/** Strongly-typed tenant identifier. Public — consumed by user, product, order modules. */
public record TenantId(UUID value) {
    public TenantId { Objects.requireNonNull(value, "value"); }
    public static TenantId newId() { return new TenantId(Ids.next()); }
    public static TenantId of(String s) { return new TenantId(UUID.fromString(s)); }
}
