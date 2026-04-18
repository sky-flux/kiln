package com.skyflux.kiln.order.adapter.in.web;

import com.skyflux.kiln.order.domain.model.Order;

import java.math.BigDecimal;
import java.util.List;

record OrderResponse(
        String id,
        String status,
        List<OrderItemResponse> items,
        BigDecimal totalAmount,
        String currency,
        String note) {

    static OrderResponse from(Order o) {
        List<OrderItemResponse> itemResps = o.items().stream()
                .map(i -> new OrderItemResponse(
                        i.productId().toString(),
                        i.productCode(),
                        i.productName(),
                        i.unitPrice().amount(),
                        i.quantity(),
                        i.subtotal().amount()))
                .toList();
        return new OrderResponse(
                o.id().value().toString(),
                o.status().name(),
                itemResps,
                o.totalAmount().amount(),
                o.totalAmount().currency().getCurrencyCode(),
                o.note());
    }
}
