package com.skyflux.kiln.order.application.usecase;

import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.common.money.Money;
import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.order.application.port.out.OrderRepository;
import com.skyflux.kiln.order.domain.event.OrderEvent;
import com.skyflux.kiln.order.domain.model.Order;
import com.skyflux.kiln.order.domain.model.OrderId;
import com.skyflux.kiln.order.domain.model.OrderItem;
import com.skyflux.kiln.order.domain.model.OrderItemId;
import com.skyflux.kiln.order.domain.model.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStateServiceTest {

    @Mock OrderRepository repo;
    @Mock ApplicationEventPublisher publisher;

    private static final UUID TENANT_ID = Ids.next();
    private static final UUID USER_ID = Ids.next();

    private Order makePendingOrder() {
        Money price = Money.of("10.00", "CNY");
        OrderItem item = new OrderItem(OrderItemId.newId(), UUID.randomUUID(),
                "P001", "Widget", price, 1, price);
        return Order.create(TENANT_ID, USER_ID, List.of(item), "note");
    }

    // ─── ConfirmOrderService ─────────────────────────────────────────────────

    @Test
    void confirmShouldTransitionToConfirmed() {
        Order pending = makePendingOrder();
        when(repo.findById(pending.id())).thenReturn(Optional.of(pending));
        ConfirmOrderService service = new ConfirmOrderService(repo);

        Order result = service.execute(pending.id());

        assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
        verify(repo).save(any(Order.class));
    }

    @Test
    void confirmShouldThrowNotFoundWhenMissing() {
        OrderId id = OrderId.newId();
        when(repo.findById(id)).thenReturn(Optional.empty());
        ConfirmOrderService service = new ConfirmOrderService(repo);

        assertThatThrownBy(() -> service.execute(id))
                .isInstanceOf(AppException.class);
    }

    // ─── ShipOrderService ────────────────────────────────────────────────────

    @Test
    void shipShouldTransitionToShipped() {
        Order confirmed = makePendingOrder().confirm();
        when(repo.findById(confirmed.id())).thenReturn(Optional.of(confirmed));
        ShipOrderService service = new ShipOrderService(repo);

        Order result = service.execute(confirmed.id());

        assertThat(result.status()).isEqualTo(OrderStatus.SHIPPED);
        verify(repo).save(any(Order.class));
    }

    // ─── DeliverOrderService ─────────────────────────────────────────────────

    @Test
    void deliverShouldTransitionToDelivered() {
        Order shipped = makePendingOrder().confirm().ship();
        when(repo.findById(shipped.id())).thenReturn(Optional.of(shipped));
        DeliverOrderService service = new DeliverOrderService(repo);

        Order result = service.execute(shipped.id());

        assertThat(result.status()).isEqualTo(OrderStatus.DELIVERED);
        verify(repo).save(any(Order.class));
    }

    // ─── CancelOrderService ──────────────────────────────────────────────────

    @Test
    void cancelShouldTransitionToCancelled() {
        Order pending = makePendingOrder();
        when(repo.findById(pending.id())).thenReturn(Optional.of(pending));
        CancelOrderService service = new CancelOrderService(repo, publisher);

        Order result = service.execute(pending.id());

        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(repo).save(any(Order.class));
    }

    @Test
    void cancelShouldPublishCancelledEvent() {
        Order pending = makePendingOrder();
        when(repo.findById(pending.id())).thenReturn(Optional.of(pending));
        CancelOrderService service = new CancelOrderService(repo, publisher);

        service.execute(pending.id());

        ArgumentCaptor<OrderEvent.OrderCancelled> captor = ArgumentCaptor.forClass(OrderEvent.OrderCancelled.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo(pending.id());
    }

    // ─── GetOrderService ─────────────────────────────────────────────────────

    @Test
    void getShouldReturnOrderWhenFound() {
        Order order = makePendingOrder();
        when(repo.findById(order.id())).thenReturn(Optional.of(order));
        GetOrderService service = new GetOrderService(repo);

        Order result = service.execute(order.id());

        assertThat(result.id()).isEqualTo(order.id());
    }

    @Test
    void getShouldThrowNotFoundWhenMissing() {
        OrderId id = OrderId.newId();
        when(repo.findById(id)).thenReturn(Optional.empty());
        GetOrderService service = new GetOrderService(repo);

        assertThatThrownBy(() -> service.execute(id))
                .isInstanceOf(AppException.class);
    }
}
