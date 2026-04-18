package com.skyflux.kiln.dict.domain;

import java.time.Instant;
import java.util.UUID;

public record DictType(
        UUID id,
        String code,
        String name,
        boolean isSystem,
        UUID tenantId,   // null = system type
        Instant createdAt
) {}
