package com.skyflux.kiln.product.adapter.out.persistence;

import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import com.skyflux.kiln.infra.jooq.generated.Tables;
import com.skyflux.kiln.infra.jooq.generated.tables.records.ProductsRecord;
import com.skyflux.kiln.product.application.port.out.ProductRepository;
import com.skyflux.kiln.product.domain.model.Product;
import com.skyflux.kiln.product.domain.model.ProductId;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * jOOQ-backed out-adapter implementing {@link ProductRepository}.
 *
 * <p>Save is an UPSERT on conflict(id) so the adapter stays idempotent for both
 * create (new aggregate) and update (loaded + mutated + saved) flows.
 * {@code created_at} is preserved across updates because the DO UPDATE clause
 * does not include it.
 */
@Repository
class ProductJooqRepositoryAdapter implements ProductRepository {

    private final DSLContext dsl;
    private final ProductMapper mapper;

    ProductJooqRepositoryAdapter(DSLContext dsl, ProductMapper mapper) {
        this.dsl = dsl;
        this.mapper = mapper;
    }

    @Override
    public Optional<Product> findById(ProductId id) {
        Objects.requireNonNull(id, "id");
        return dsl.selectFrom(Tables.PRODUCTS)
                .where(Tables.PRODUCTS.ID.eq(id.value()))
                .fetchOptional()
                .map(mapper::toAggregate);
    }

    @Override
    public void save(Product product) {
        Objects.requireNonNull(product, "product");
        ProductsRecord r = mapper.toRecord(product);
        dsl.insertInto(Tables.PRODUCTS)
                .set(r)
                .onConflict(Tables.PRODUCTS.ID)
                .doUpdate()
                // INVARIANT: do NOT include CREATED_AT — preserve original insert timestamp.
                .set(Tables.PRODUCTS.TENANT_ID, r.getTenantId())
                .set(Tables.PRODUCTS.CODE, r.getCode())
                .set(Tables.PRODUCTS.NAME, r.getName())
                .set(Tables.PRODUCTS.DESCRIPTION, r.getDescription())
                .set(Tables.PRODUCTS.PRICE_AMOUNT, r.getPriceAmount())
                .set(Tables.PRODUCTS.PRICE_CURRENCY, r.getPriceCurrency())
                .set(Tables.PRODUCTS.STATUS, r.getStatus())
                .set(Tables.PRODUCTS.UPDATED_AT, r.getUpdatedAt())
                .execute();
    }

    @Override
    public PageResult<Product> listActive(PageQuery query) {
        Objects.requireNonNull(query, "query");
        var cond = Tables.PRODUCTS.STATUS.eq("ACTIVE");
        long total = Optional.ofNullable(
                dsl.selectCount().from(Tables.PRODUCTS).where(cond).fetchOne(0, Long.class)
        ).orElse(0L);
        List<Product> items = dsl.selectFrom(Tables.PRODUCTS)
                .where(cond)
                .orderBy(Tables.PRODUCTS.CREATED_AT.desc())
                .limit(query.size()).offset(query.offset())
                .fetch()
                .map(mapper::toAggregate);
        return PageResult.of(items, total, query);
    }
}
