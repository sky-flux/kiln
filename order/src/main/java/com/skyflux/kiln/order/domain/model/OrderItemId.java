package com.skyflux.kiln.order.domain.model;

import com.skyflux.kiln.common.util.Ids;

import java.util.Objects;
import java.util.UUID;

public record OrderItemId(UUID value) {
    public OrderItemId {
        Objects.requireNonNull(value, "value");
    }

    public static OrderItemId newId() {
        return new OrderItemId(Ids.next());
    }
}
