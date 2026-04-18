package com.skyflux.kiln.order.domain.model;

import com.skyflux.kiln.common.money.Money;
import com.skyflux.kiln.order.domain.exception.InvalidOrderTransitionException;

import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class Order {
    private final OrderId id;
    private final UUID tenantId;
    private final UUID userId;
    private final List<OrderItem> items;
    private final OrderStatus status;
    private final Money totalAmount;
    private final String note;

    private Order(OrderId id, UUID tenantId, UUID userId, List<OrderItem> items,
                  OrderStatus status, Money totalAmount, String note) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.userId = Objects.requireNonNull(userId);
        this.items = List.copyOf(Objects.requireNonNull(items));
        this.status = Objects.requireNonNull(status);
        this.totalAmount = Objects.requireNonNull(totalAmount);
        this.note = note;
        if (items.isEmpty()) throw new IllegalArgumentException("order must have at least one item");
    }

    public static Order create(UUID tenantId, UUID userId, List<OrderItem> items, String note) {
        if (items.isEmpty()) throw new IllegalArgumentException("order must have at least one item");
        Currency currency = items.get(0).subtotal().currency();
        Money total = items.stream().map(OrderItem::subtotal)
            .reduce(Money.zero(currency), Money::add);
        return new Order(OrderId.newId(), tenantId, userId, items, OrderStatus.PENDING, total, note);
    }

    public static Order reconstitute(OrderId id, UUID tenantId, UUID userId, List<OrderItem> items,
                                      OrderStatus status, Money totalAmount, String note) {
        return new Order(id, tenantId, userId, items, status, totalAmount, note);
    }

    private Order transition(OrderStatus target, OrderStatus... allowed) {
        for (OrderStatus a : allowed) {
            if (status == a) return withStatus(target);
        }
        throw new InvalidOrderTransitionException(status, target);
    }

    private Order withStatus(OrderStatus s) {
        return new Order(id, tenantId, userId, items, s, totalAmount, note);
    }

    public Order confirm()  { return transition(OrderStatus.CONFIRMED, OrderStatus.PENDING); }
    public Order pay()      { return transition(OrderStatus.PAID, OrderStatus.CONFIRMED); }
    public Order ship()     { return transition(OrderStatus.SHIPPED, OrderStatus.PAID); }
    public Order deliver()  { return transition(OrderStatus.DELIVERED, OrderStatus.SHIPPED); }
    public Order cancel()   { return transition(OrderStatus.CANCELLED, OrderStatus.PENDING, OrderStatus.CONFIRMED); }

    public OrderId id()            { return id; }
    public UUID tenantId()         { return tenantId; }
    public UUID userId()           { return userId; }
    public List<OrderItem> items() { return items; }
    public OrderStatus status()    { return status; }
    public Money totalAmount()     { return totalAmount; }
    public String note()           { return note; }
}
