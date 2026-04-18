package com.skyflux.kiln.order.adapter.in.web;

import com.skyflux.kiln.common.money.Money;
import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.order.application.port.in.CancelOrderUseCase;
import com.skyflux.kiln.order.application.port.in.ConfirmOrderUseCase;
import com.skyflux.kiln.order.application.port.in.CreateOrderUseCase;
import com.skyflux.kiln.order.application.port.in.DeliverOrderUseCase;
import com.skyflux.kiln.order.application.port.in.GetOrderUseCase;
import com.skyflux.kiln.order.application.port.in.ListOrdersUseCase;
import com.skyflux.kiln.order.application.port.in.PayOrderUseCase;
import com.skyflux.kiln.order.application.port.in.ShipOrderUseCase;
import com.skyflux.kiln.order.domain.model.Order;
import com.skyflux.kiln.order.domain.model.OrderId;
import com.skyflux.kiln.order.domain.model.OrderItem;
import com.skyflux.kiln.order.domain.model.OrderItemId;
import com.skyflux.kiln.order.domain.model.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(basePackageClasses = OrderController.class)
    static class BootConfig {}

    @MockitoBean CreateOrderUseCase createUseCase;
    @MockitoBean ConfirmOrderUseCase confirmUseCase;
    @MockitoBean ShipOrderUseCase shipUseCase;
    @MockitoBean DeliverOrderUseCase deliverUseCase;
    @MockitoBean CancelOrderUseCase cancelUseCase;
    @MockitoBean GetOrderUseCase getUseCase;
    @MockitoBean ListOrdersUseCase listUseCase;
    @MockitoBean PayOrderUseCase payUseCase;

    @Autowired MockMvc mvc;

    private static final UUID TENANT_ID = Ids.next();
    private static final UUID USER_ID = Ids.next();
    private static final UUID PRODUCT_ID = Ids.next();

    private static Order sampleOrder(OrderStatus status) {
        Money price = Money.of("10.00", "CNY");
        OrderItem item = new OrderItem(OrderItemId.newId(), PRODUCT_ID, "P001", "Widget",
                price, 1, price);
        List<OrderItem> items = List.of(item);
        Order base = Order.create(TENANT_ID, USER_ID, items, "note");
        return switch (status) {
            case PENDING -> base;
            case CONFIRMED -> base.confirm();
            case PAID -> base.confirm().pay();
            case SHIPPED -> base.confirm().pay().ship();
            case DELIVERED -> base.confirm().pay().ship().deliver();
            case CANCELLED -> base.cancel();
        };
    }

    @Test
    void shouldCreateOrderAndReturn201() throws Exception {
        Order order = sampleOrder(OrderStatus.PENDING);
        when(createUseCase.execute(any(CreateOrderUseCase.Command.class))).thenReturn(order);

        String body = """
                {
                  "items": [{
                    "productId": "%s",
                    "productCode": "P001",
                    "productName": "Widget",
                    "unitPrice": 10.00,
                    "currency": "CNY",
                    "quantity": 1
                  }],
                  "note": "note"
                }
                """.formatted(PRODUCT_ID);

        mvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].productCode").value("P001"));
    }

    @Test
    void shouldListOrdersAndReturn200() throws Exception {
        Order order = sampleOrder(OrderStatus.PENDING);
        PageQuery query = new PageQuery(1, 20, null);
        when(listUseCase.execute(any(ListOrdersUseCase.Command.class)))
                .thenReturn(PageResult.of(List.of(order), 1L, query));

        mvc.perform(get("/api/v1/orders")
                        .param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void shouldConfirmOrderAndReturn200() throws Exception {
        Order confirmed = sampleOrder(OrderStatus.CONFIRMED);
        when(confirmUseCase.execute(any(OrderId.class))).thenReturn(confirmed);

        mvc.perform(post("/api/v1/orders/" + confirmed.id().value() + "/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    void shouldGetOrderAndReturn200() throws Exception {
        Order order = sampleOrder(OrderStatus.PENDING);
        when(getUseCase.execute(any(OrderId.class))).thenReturn(order);

        mvc.perform(get("/api/v1/orders/" + order.id().value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void shouldPayOrderAndReturn200() throws Exception {
        Order paid = sampleOrder(OrderStatus.PAID);
        when(payUseCase.execute(any(OrderId.class))).thenReturn(paid);

        mvc.perform(post("/api/v1/orders/" + paid.id().value() + "/pay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));
    }
}
