package com.skyflux.kiln.dict.domain;

import java.time.Instant;
import java.util.UUID;

public record DictItem(
        UUID id,
        UUID typeId,
        String code,
        String label,
        int sortOrder,
        boolean isActive,
        UUID tenantId,   // null = system item
        Instant createdAt
) {}
