package com.skyflux.kiln.common.result;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PageQuery(
        @Min(1) int page,
        @Min(1) @Max(200) int size,
        String sort
) {
    public PageQuery {
        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > 200) size = 200;
    }

    public int offset() {
        return (page - 1) * size;
    }
}
