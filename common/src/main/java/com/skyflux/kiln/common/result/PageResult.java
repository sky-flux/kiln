package com.skyflux.kiln.common.result;

import java.util.List;
import java.util.function.Function;

public record PageResult<T>(
        List<T> items,
        long total,
        int page,
        int size
) {
    public static <T> PageResult<T> of(List<T> items, long total, PageQuery query) {
        return new PageResult<>(items, total, query.page(), query.size());
    }

    public static <T> PageResult<T> empty(PageQuery query) {
        return new PageResult<>(List.of(), 0L, query.page(), query.size());
    }

    /** Transform items while preserving pagination metadata. */
    public <U> PageResult<U> map(Function<T, U> mapper) {
        return new PageResult<>(items.stream().map(mapper).toList(), total, page, size);
    }
}
