package com.skyflux.kiln.order.api;

import com.skyflux.kiln.common.money.Money;
import com.skyflux.kiln.order.domain.model.OrderId;

import java.time.Instant;
import java.util.UUID;

/**
 * Public domain events published by the order module.
 * Placed in order.api so other modules (member, audit) can listen
 * without crossing into order.domain.
 */
public sealed interface OrderEvent {
    record OrderCreated(OrderId orderId, UUID tenantId, UUID userId, Instant occurredAt)
        implements OrderEvent {}
    record OrderPaid(OrderId orderId, UUID tenantId, UUID userId, Money amount, Instant occurredAt)
        implements OrderEvent {}
    record OrderCancelled(OrderId orderId, UUID tenantId, Instant occurredAt)
        implements OrderEvent {}
}
