package com.skyflux.kiln.order.domain.model;

import com.skyflux.kiln.common.money.Money;

import java.util.Objects;
import java.util.UUID;

public record OrderItem(
    OrderItemId id, UUID productId, String productCode, String productName,
    Money unitPrice, int quantity, Money subtotal
) {
    public OrderItem {
        Objects.requireNonNull(id);
        Objects.requireNonNull(productId);
        Objects.requireNonNull(productCode);
        Objects.requireNonNull(productName);
        Objects.requireNonNull(unitPrice);
        Objects.requireNonNull(subtotal);
        if (quantity < 1) throw new IllegalArgumentException("quantity < 1");
    }
}
