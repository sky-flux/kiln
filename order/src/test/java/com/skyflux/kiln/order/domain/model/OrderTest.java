package com.skyflux.kiln.order.domain.model;

import com.skyflux.kiln.common.money.Money;
import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.order.domain.exception.InvalidOrderTransitionException;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class OrderTest {

    private static Order sampleOrder() {
        UUID tenantId = Ids.next();
        UUID userId = Ids.next();
        OrderItem item = new OrderItem(new OrderItemId(Ids.next()), UUID.randomUUID(),
            "SKU-1", "Widget", Money.of("10.00", "CNY"), 2, Money.of("20.00", "CNY"));
        return Order.create(tenantId, userId, List.of(item), null);
    }

    @Test void shouldCreateWithPendingStatus() {
        assertThat(sampleOrder().status()).isEqualTo(OrderStatus.PENDING);
    }
    @Test void shouldCalculateTotalFromItems() {
        assertThat(sampleOrder().totalAmount().amount()).isEqualByComparingTo("20.00");
    }
    @Test void shouldConfirmPendingOrder() {
        assertThat(sampleOrder().confirm().status()).isEqualTo(OrderStatus.CONFIRMED);
    }
    @Test void shouldRejectConfirmingNonPendingOrder() {
        assertThatThrownBy(() -> sampleOrder().confirm().confirm())
            .isInstanceOf(InvalidOrderTransitionException.class);
    }
    @Test void shouldShipConfirmedOrder() {
        assertThat(sampleOrder().confirm().ship().status()).isEqualTo(OrderStatus.SHIPPED);
    }
    @Test void shouldRejectShippingPendingOrder() {
        assertThatThrownBy(() -> sampleOrder().ship())
            .isInstanceOf(InvalidOrderTransitionException.class);
    }
    @Test void shouldDeliverShippedOrder() {
        assertThat(sampleOrder().confirm().ship().deliver().status()).isEqualTo(OrderStatus.DELIVERED);
    }
    @Test void shouldCancelPendingOrder() {
        assertThat(sampleOrder().cancel().status()).isEqualTo(OrderStatus.CANCELLED);
    }
    @Test void shouldCancelConfirmedOrder() {
        assertThat(sampleOrder().confirm().cancel().status()).isEqualTo(OrderStatus.CANCELLED);
    }
    @Test void shouldRejectCancellingDeliveredOrder() {
        assertThatThrownBy(() -> sampleOrder().confirm().ship().deliver().cancel())
            .isInstanceOf(InvalidOrderTransitionException.class);
    }
    @Test void shouldRejectEmptyItemList() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            Order.create(Ids.next(), Ids.next(), List.of(), null));
    }
}
