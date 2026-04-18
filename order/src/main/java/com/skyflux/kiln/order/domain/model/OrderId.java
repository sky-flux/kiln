package com.skyflux.kiln.order.domain.model;

import com.skyflux.kiln.common.util.Ids;

import java.util.Objects;
import java.util.UUID;

public record OrderId(UUID value) {
    public OrderId {
        Objects.requireNonNull(value, "value");
    }

    public static OrderId newId() {
        return new OrderId(Ids.next());
    }

    public static OrderId of(String s) {
        return new OrderId(UUID.fromString(s));
    }
}
