package com.skyflux.kiln.order.domain.event;

import com.skyflux.kiln.order.domain.model.OrderId;

import java.time.Instant;
import java.util.UUID;

public sealed interface OrderEvent {
    record OrderCreated(OrderId orderId, UUID tenantId, UUID userId, Instant occurredAt) implements OrderEvent {}
    record OrderCancelled(OrderId orderId, UUID tenantId, Instant occurredAt) implements OrderEvent {}
}
