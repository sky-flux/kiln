package com.skyflux.kiln.order.application.usecase;

import com.skyflux.kiln.common.money.Money;
import com.skyflux.kiln.order.application.port.in.CreateOrderUseCase;
import com.skyflux.kiln.order.application.port.out.OrderRepository;
import com.skyflux.kiln.order.domain.event.OrderEvent;
import com.skyflux.kiln.order.domain.model.Order;
import com.skyflux.kiln.order.domain.model.OrderItem;
import com.skyflux.kiln.order.domain.model.OrderItemId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class CreateOrderService implements CreateOrderUseCase {

    private final OrderRepository repo;
    private final ApplicationEventPublisher publisher;

    public CreateOrderService(OrderRepository repo, ApplicationEventPublisher publisher) {
        this.repo = repo;
        this.publisher = publisher;
    }

    @Override
    @Transactional
    public Order execute(Command cmd) {
        List<OrderItem> items = cmd.items().stream().map(i -> {
            Money unitPrice = Money.of(i.unitPrice().toPlainString(), i.currency());
            Money subtotal = unitPrice.multiply(java.math.BigDecimal.valueOf(i.quantity()));
            return new OrderItem(
                    OrderItemId.newId(),
                    i.productId(),
                    i.productCode(),
                    i.productName(),
                    unitPrice,
                    i.quantity(),
                    subtotal);
        }).toList();

        Order order = Order.create(cmd.tenantId(), cmd.userId(), items, cmd.note());
        repo.save(order);
        publisher.publishEvent(new OrderEvent.OrderCreated(
                order.id(), order.tenantId(), order.userId(), Instant.now()));
        return order;
    }
}
