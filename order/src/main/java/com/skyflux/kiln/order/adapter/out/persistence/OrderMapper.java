package com.skyflux.kiln.order.adapter.out.persistence;

import com.skyflux.kiln.common.money.Money;
import com.skyflux.kiln.infra.jooq.generated.tables.records.OrderItemsRecord;
import com.skyflux.kiln.infra.jooq.generated.tables.records.OrdersRecord;
import com.skyflux.kiln.order.domain.model.Order;
import com.skyflux.kiln.order.domain.model.OrderId;
import com.skyflux.kiln.order.domain.model.OrderItem;
import com.skyflux.kiln.order.domain.model.OrderItemId;
import com.skyflux.kiln.order.domain.model.OrderStatus;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.util.Currency;
import java.util.List;

/**
 * Translates between {@link Order} / {@link OrderItem} aggregates and the
 * jOOQ-generated records. Package-private — only the adapter and its tests use it.
 */
@Component
class OrderMapper {

    Order toAggregate(OrdersRecord header, List<OrderItemsRecord> itemRecords) {
        List<OrderItem> items = itemRecords.stream()
                .map(this::toOrderItem)
                .toList();
        Currency totalCurrency = Currency.getInstance(header.getTotalCurrency());
        Money totalAmount = new Money(
                header.getTotalAmount().setScale(
                        totalCurrency.getDefaultFractionDigits(), RoundingMode.UNNECESSARY),
                totalCurrency);
        return Order.reconstitute(
                new OrderId(header.getId()),
                header.getTenantId(),
                header.getUserId(),
                items,
                OrderStatus.valueOf(header.getStatus()),
                totalAmount,
                header.getNote());
    }

    OrderItem toOrderItem(OrderItemsRecord r) {
        Currency currency = Currency.getInstance(r.getCurrency());
        Money unitPrice = new Money(
                r.getUnitPrice().setScale(currency.getDefaultFractionDigits(), RoundingMode.UNNECESSARY),
                currency);
        Money subtotal = new Money(
                r.getSubtotal().setScale(currency.getDefaultFractionDigits(), RoundingMode.UNNECESSARY),
                currency);
        return new OrderItem(
                new OrderItemId(r.getId()),
                r.getProductId(),
                r.getProductCode(),
                r.getProductName(),
                unitPrice,
                r.getQuantity(),
                subtotal);
    }
}
