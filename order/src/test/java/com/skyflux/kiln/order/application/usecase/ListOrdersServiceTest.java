package com.skyflux.kiln.order.application.usecase;

import com.skyflux.kiln.common.money.Money;
import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.order.application.port.in.ListOrdersUseCase;
import com.skyflux.kiln.order.application.port.out.OrderRepository;
import com.skyflux.kiln.order.domain.model.Order;
import com.skyflux.kiln.order.domain.model.OrderItem;
import com.skyflux.kiln.order.domain.model.OrderItemId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListOrdersServiceTest {

    @Mock OrderRepository repo;
    @InjectMocks ListOrdersService service;

    private static final UUID TENANT_ID = Ids.next();
    private static final UUID USER_ID = Ids.next();

    private Order makeOrder() {
        Money price = Money.of("10.00", "CNY");
        OrderItem item = new OrderItem(OrderItemId.newId(), UUID.randomUUID(),
                "P001", "Widget", price, 1, price);
        return Order.create(TENANT_ID, USER_ID, List.of(item), null);
    }

    @Test
    void shouldDelegateToRepoListByUser() {
        PageQuery query = new PageQuery(1, 10, null);
        Order order = makeOrder();
        PageResult<Order> expected = PageResult.of(List.of(order), 1L, query);
        when(repo.listByUser(eq(USER_ID), any())).thenReturn(expected);

        ListOrdersUseCase.Command cmd = new ListOrdersUseCase.Command(USER_ID, query);
        PageResult<Order> result = service.execute(cmd);

        assertThat(result.items()).hasSize(1);
        verify(repo).listByUser(eq(USER_ID), eq(query));
    }
}
