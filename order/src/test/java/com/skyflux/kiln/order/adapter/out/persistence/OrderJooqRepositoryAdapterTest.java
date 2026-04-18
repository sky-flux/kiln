package com.skyflux.kiln.order.adapter.out.persistence;

import com.skyflux.kiln.common.money.Money;
import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.infra.jooq.generated.Tables;
import com.skyflux.kiln.order.application.port.out.OrderRepository;
import com.skyflux.kiln.order.domain.model.Order;
import com.skyflux.kiln.order.domain.model.OrderId;
import com.skyflux.kiln.order.domain.model.OrderItem;
import com.skyflux.kiln.order.domain.model.OrderItemId;
import com.skyflux.kiln.order.domain.model.OrderStatus;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link OrderJooqRepositoryAdapter}.
 *
 * Uses a real PostgreSQL container + Flyway to exercise genuine SQL.
 */
@SpringBootTest(classes = OrderJooqRepositoryAdapterTest.TestApp.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderJooqRepositoryAdapterTest {

    static final String POSTGRES_IMAGE = "postgres:18.3-alpine";

    private static final UUID TENANT_ID = Ids.next();
    private static final UUID USER_ID = Ids.next();
    private static final UUID PRODUCT_ID = Ids.next();

    @SpringBootApplication(exclude = {
            org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration.class,
            cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate.class
    })
    static class TestApp {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>(POSTGRES_IMAGE);
        }
    }

    @Autowired
    OrderRepository repo;

    @Autowired
    DSLContext dsl;

    @BeforeAll
    void seedTenantAndUser() {
        dsl.insertInto(Tables.TENANTS)
                .set(Tables.TENANTS.ID, TENANT_ID)
                .set(Tables.TENANTS.CODE, "order-test-tenant")
                .set(Tables.TENANTS.NAME, "Order Test Tenant")
                .set(Tables.TENANTS.CREATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .onConflictDoNothing()
                .execute();

        dsl.insertInto(Tables.USERS)
                .set(Tables.USERS.ID, USER_ID)
                .set(Tables.USERS.TENANT_ID, TENANT_ID)
                .set(Tables.USERS.NAME, "Order Tester")
                .set(Tables.USERS.EMAIL, "ordertester@example.com")
                .set(Tables.USERS.PASSWORD_HASH, "$argon2id$v=19$test")
                .set(Tables.USERS.STATUS, "ACTIVE")
                .set(Tables.USERS.CREATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .set(Tables.USERS.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .onConflictDoNothing()
                .execute();
    }

    private OrderItem makeItem(String price, String currency) {
        Money unitPrice = Money.of(price, currency);
        Money subtotal = unitPrice.multiply(BigDecimal.ONE);
        return new OrderItem(OrderItemId.newId(), PRODUCT_ID, "P001", "Widget",
                unitPrice, 1, subtotal);
    }

    private Order createPendingOrder() {
        List<OrderItem> items = List.of(makeItem("10.00", "CNY"), makeItem("5.00", "CNY"));
        return Order.create(TENANT_ID, USER_ID, items, "test note");
    }

    @Test
    void shouldSaveAndFindById_withItems() {
        Order order = createPendingOrder();
        repo.save(order);

        Optional<Order> found = repo.findById(order.id());
        assertThat(found).isPresent();
        Order loaded = found.get();
        assertThat(loaded.id()).isEqualTo(order.id());
        assertThat(loaded.tenantId()).isEqualTo(TENANT_ID);
        assertThat(loaded.userId()).isEqualTo(USER_ID);
        assertThat(loaded.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(loaded.items()).hasSize(2);
        assertThat(loaded.note()).isEqualTo("test note");
        assertThat(loaded.totalAmount().currency()).isEqualTo(Currency.getInstance("CNY"));
    }

    @Test
    void shouldUpdateStatusOnSave() {
        Order order = createPendingOrder();
        repo.save(order);

        Order confirmed = order.confirm();
        repo.save(confirmed);

        Order loaded = repo.findById(order.id()).orElseThrow();
        assertThat(loaded.status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void shouldReturnEmptyForUnknownId() {
        assertThat(repo.findById(OrderId.newId())).isEmpty();
    }

    @Test
    void shouldListByUser() {
        // Create 2 orders for our user
        Order o1 = createPendingOrder();
        Order o2 = createPendingOrder();
        repo.save(o1);
        repo.save(o2);

        PageResult<Order> result = repo.listByUser(USER_ID, new PageQuery(1, 10, null));
        assertThat(result.items()).isNotEmpty();
        // Both orders must appear
        List<OrderId> ids = result.items().stream().map(Order::id).toList();
        assertThat(ids).contains(o1.id(), o2.id());
    }

    @Test
    void shouldListAll() {
        Order order = createPendingOrder();
        repo.save(order);

        PageResult<Order> result = repo.listAll(new PageQuery(1, 100, null));
        assertThat(result.items()).isNotEmpty();
        assertThat(result.total()).isGreaterThanOrEqualTo(1);
    }
}
