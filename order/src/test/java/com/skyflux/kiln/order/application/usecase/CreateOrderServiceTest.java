package com.skyflux.kiln.order.application.usecase;

import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.order.application.port.in.CreateOrderUseCase;
import com.skyflux.kiln.order.application.port.out.OrderRepository;
import com.skyflux.kiln.order.domain.event.OrderEvent;
import com.skyflux.kiln.order.domain.model.Order;
import com.skyflux.kiln.order.domain.model.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CreateOrderServiceTest {

    @Mock OrderRepository repo;
    @Mock ApplicationEventPublisher publisher;
    @InjectMocks CreateOrderService service;

    private static final UUID TENANT_ID = Ids.next();
    private static final UUID USER_ID = Ids.next();
    private static final UUID PRODUCT_ID = Ids.next();

    private CreateOrderUseCase.Command makeCommand() {
        return new CreateOrderUseCase.Command(
                TENANT_ID,
                USER_ID,
                List.of(new CreateOrderUseCase.ItemRequest(
                        PRODUCT_ID, "P001", "Widget",
                        new BigDecimal("10.00"), "CNY", 2)),
                "my note");
    }

    @Test
    void shouldCreateOrderWithPendingStatusAndCorrectTotal() {
        Order result = service.execute(makeCommand());

        assertThat(result.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.items()).hasSize(1);
        // 10.00 * 2 = 20.00 CNY
        assertThat(result.totalAmount().amount()).isEqualByComparingTo("20.00");
        assertThat(result.totalAmount().currency().getCurrencyCode()).isEqualTo("CNY");
        assertThat(result.note()).isEqualTo("my note");
    }

    @Test
    void shouldSaveOrderToRepository() {
        service.execute(makeCommand());
        verify(repo).save(any(Order.class));
    }

    @Test
    void shouldPublishOrderCreatedEvent() {
        service.execute(makeCommand());

        ArgumentCaptor<OrderEvent.OrderCreated> captor = ArgumentCaptor.forClass(OrderEvent.OrderCreated.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
    }
}
