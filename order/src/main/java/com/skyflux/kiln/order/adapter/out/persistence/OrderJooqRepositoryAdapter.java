package com.skyflux.kiln.order.adapter.out.persistence;

import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import com.skyflux.kiln.infra.jooq.generated.Tables;
import com.skyflux.kiln.order.application.port.out.OrderRepository;
import com.skyflux.kiln.order.domain.model.Order;
import com.skyflux.kiln.order.domain.model.OrderId;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * jOOQ-backed implementation of {@link OrderRepository}.
 *
 * <p>Save is an UPSERT on conflict(id): status and updated_at are refreshed on
 * each call; created_at is NOT in the DO UPDATE clause so it is preserved.
 * Items are deleted and re-inserted on every save (simple and correct for the
 * current aggregate size; optimise with change-tracking if needed later).
 */
@Repository
class OrderJooqRepositoryAdapter implements OrderRepository {

    private final DSLContext dsl;
    private final OrderMapper mapper;

    OrderJooqRepositoryAdapter(DSLContext dsl, OrderMapper mapper) {
        this.dsl = dsl;
        this.mapper = mapper;
    }

    @Override
    public Optional<Order> findById(OrderId id) {
        Objects.requireNonNull(id, "id");
        var header = dsl.selectFrom(Tables.ORDERS)
                .where(Tables.ORDERS.ID.eq(id.value()))
                .fetchOne();
        if (header == null) return Optional.empty();
        var items = dsl.selectFrom(Tables.ORDER_ITEMS)
                .where(Tables.ORDER_ITEMS.ORDER_ID.eq(id.value()))
                .fetch();
        return Optional.of(mapper.toAggregate(header, items));
    }

    @Override
    @Transactional
    public void save(Order order) {
        Objects.requireNonNull(order, "order");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        dsl.insertInto(Tables.ORDERS)
                .set(Tables.ORDERS.ID, order.id().value())
                .set(Tables.ORDERS.TENANT_ID, order.tenantId())
                .set(Tables.ORDERS.USER_ID, order.userId())
                .set(Tables.ORDERS.STATUS, order.status().name())
                .set(Tables.ORDERS.TOTAL_AMOUNT, order.totalAmount().amount())
                .set(Tables.ORDERS.TOTAL_CURRENCY, order.totalAmount().currency().getCurrencyCode())
                .set(Tables.ORDERS.NOTE, order.note())
                .set(Tables.ORDERS.CREATED_AT, now)
                .set(Tables.ORDERS.UPDATED_AT, now)
                .onConflict(Tables.ORDERS.ID)
                .doUpdate()
                // INVARIANT: do NOT include CREATED_AT — preserve original insert timestamp.
                .set(Tables.ORDERS.STATUS, order.status().name())
                .set(Tables.ORDERS.UPDATED_AT, now)
                .execute();

        // Replace items entirely on each save
        dsl.deleteFrom(Tables.ORDER_ITEMS)
                .where(Tables.ORDER_ITEMS.ORDER_ID.eq(order.id().value()))
                .execute();

        for (var item : order.items()) {
            dsl.insertInto(Tables.ORDER_ITEMS)
                    .set(Tables.ORDER_ITEMS.ID, item.id().value())
                    .set(Tables.ORDER_ITEMS.ORDER_ID, order.id().value())
                    .set(Tables.ORDER_ITEMS.PRODUCT_ID, item.productId())
                    .set(Tables.ORDER_ITEMS.PRODUCT_CODE, item.productCode())
                    .set(Tables.ORDER_ITEMS.PRODUCT_NAME, item.productName())
                    .set(Tables.ORDER_ITEMS.UNIT_PRICE, item.unitPrice().amount())
                    .set(Tables.ORDER_ITEMS.CURRENCY, item.unitPrice().currency().getCurrencyCode())
                    .set(Tables.ORDER_ITEMS.QUANTITY, item.quantity())
                    .set(Tables.ORDER_ITEMS.SUBTOTAL, item.subtotal().amount())
                    .execute();
        }
    }

    @Override
    public PageResult<Order> listByUser(UUID userId, PageQuery query) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(query, "query");
        var cond = Tables.ORDERS.USER_ID.eq(userId);
        long total = Optional.ofNullable(
                dsl.selectCount().from(Tables.ORDERS).where(cond).fetchOne(0, Long.class)
        ).orElse(0L);
        var headers = dsl.selectFrom(Tables.ORDERS)
                .where(cond)
                .orderBy(Tables.ORDERS.CREATED_AT.desc())
                .limit(query.size()).offset(query.offset())
                .fetch();
        List<Order> orders = headers.stream().map(h -> {
            var items = dsl.selectFrom(Tables.ORDER_ITEMS)
                    .where(Tables.ORDER_ITEMS.ORDER_ID.eq(h.getId())).fetch();
            return mapper.toAggregate(h, items);
        }).toList();
        return PageResult.of(orders, total, query);
    }

    @Override
    public PageResult<Order> listAll(PageQuery query) {
        Objects.requireNonNull(query, "query");
        long total = Optional.ofNullable(
                dsl.selectCount().from(Tables.ORDERS).fetchOne(0, Long.class)
        ).orElse(0L);
        var headers = dsl.selectFrom(Tables.ORDERS)
                .orderBy(Tables.ORDERS.CREATED_AT.desc())
                .limit(query.size()).offset(query.offset())
                .fetch();
        List<Order> orders = headers.stream().map(h -> {
            var items = dsl.selectFrom(Tables.ORDER_ITEMS)
                    .where(Tables.ORDER_ITEMS.ORDER_ID.eq(h.getId())).fetch();
            return mapper.toAggregate(h, items);
        }).toList();
        return PageResult.of(orders, total, query);
    }
}
