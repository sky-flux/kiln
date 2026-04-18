package com.skyflux.kiln.order.application.usecase;

import com.skyflux.kiln.common.money.Money;
import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.order.api.OrderEvent;
import com.skyflux.kiln.order.application.port.out.OrderRepository;
import com.skyflux.kiln.order.domain.model.Order;
import com.skyflux.kiln.order.domain.model.OrderId;
import com.skyflux.kiln.order.domain.model.OrderItem;
import com.skyflux.kiln.order.domain.model.OrderItemId;
import com.skyflux.kiln.order.domain.model.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayOrderServiceTest {

    @Mock OrderRepository repo;
    @Mock ApplicationEventPublisher events;
    @Mock Clock clock;
    @InjectMocks PayOrderService service;

    private Order confirmedOrder() {
        OrderItem item = new OrderItem(OrderItemId.newId(), Ids.next(),
            "SKU-1", "Widget", Money.of("100.00", "CNY"), 1, Money.of("100.00", "CNY"));
        return Order.create(Ids.next(), Ids.next(), List.of(item), null).confirm();
    }

    @Test void shouldTransitionConfirmedToPaid() {
        Order order = confirmedOrder();
        when(repo.findById(order.id())).thenReturn(Optional.of(order));
        when(clock.instant()).thenReturn(java.time.Instant.now());

        Order result = service.execute(order.id());

        assertThat(result.status()).isEqualTo(OrderStatus.PAID);
        verify(repo).save(argThat(o -> o.status() == OrderStatus.PAID));
    }

    @Test void shouldPublishOrderPaidEvent() {
        Order order = confirmedOrder();
        when(repo.findById(order.id())).thenReturn(Optional.of(order));
        when(clock.instant()).thenReturn(java.time.Instant.now());

        service.execute(order.id());

        ArgumentCaptor<OrderEvent.OrderPaid> cap = ArgumentCaptor.forClass(OrderEvent.OrderPaid.class);
        verify(events).publishEvent(cap.capture());
        assertThat(cap.getValue().orderId()).isEqualTo(order.id());
        assertThat(cap.getValue().amount().amount()).isEqualByComparingTo("100.00");
    }

    @Test void shouldThrowNotFoundForUnknownOrder() {
        OrderId id = OrderId.newId();
        when(repo.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.execute(id))
            .isInstanceOf(com.skyflux.kiln.common.exception.AppException.class);
    }
}
