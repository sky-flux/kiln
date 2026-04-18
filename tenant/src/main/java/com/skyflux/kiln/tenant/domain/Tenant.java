package com.skyflux.kiln.tenant.domain;

import com.skyflux.kiln.tenant.api.TenantId;
import java.time.Instant;

public record Tenant(TenantId id, String code, String name, String status, Instant createdAt) {
    public boolean isActive() { return "ACTIVE".equals(status); }
}
