package com.skyflux.kiln.product.domain.model;

import com.skyflux.kiln.common.util.Ids;

import java.util.Objects;
import java.util.UUID;

public record ProductId(UUID value) {
    public ProductId {
        Objects.requireNonNull(value, "value");
    }

    public static ProductId newId() {
        return new ProductId(Ids.next());
    }

    public static ProductId of(String s) {
        return new ProductId(UUID.fromString(s));
    }
}
