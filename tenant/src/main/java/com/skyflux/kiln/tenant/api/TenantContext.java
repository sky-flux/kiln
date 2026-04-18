package com.skyflux.kiln.tenant.api;

import java.util.UUID;

/**
 * Holds the current request's tenant ID in a Java 25 ScopedValue.
 * Bound per-request by TenantFilter; consumed by TenantRlsListener.
 */
public final class TenantContext {
    public static final ScopedValue<UUID> CURRENT = ScopedValue.newInstance();
    private TenantContext() {}
}
