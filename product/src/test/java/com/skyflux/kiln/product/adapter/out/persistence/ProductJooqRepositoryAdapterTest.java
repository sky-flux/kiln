package com.skyflux.kiln.product.adapter.out.persistence;

import com.skyflux.kiln.common.money.Money;
import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.infra.jooq.generated.Tables;
import com.skyflux.kiln.product.application.port.out.ProductRepository;
import com.skyflux.kiln.product.domain.model.Product;
import com.skyflux.kiln.product.domain.model.ProductId;
import com.skyflux.kiln.tenant.api.TenantContext;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link ProductJooqRepositoryAdapter}.
 * Uses a real PostgreSQL container via Testcontainers + {@code @ServiceConnection}.
 * Flyway migrations run automatically, including V10__product.sql.
 */
@SpringBootTest(classes = ProductJooqRepositoryAdapterTest.TestApp.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductJooqRepositoryAdapterTest {

    static final String POSTGRES_IMAGE = "postgres:18.3-alpine";

    /** Shared tenant ID — seeded once per test suite run. */
    static final UUID SYSTEM_TENANT_ID = Ids.next();

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
    ProductRepository repo;

    @Autowired
    ProductJooqRepositoryAdapter jooqAdapter;

    @Autowired
    DSLContext dsl;

    @BeforeAll
    void seedTenant() {
        dsl.insertInto(Tables.TENANTS)
                .set(Tables.TENANTS.ID, SYSTEM_TENANT_ID)
                .set(Tables.TENANTS.CODE, "product-test-tenant")
                .set(Tables.TENANTS.NAME, "Product Test Tenant")
                .set(Tables.TENANTS.CREATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .onConflictDoNothing()
                .execute();
    }

    private Product createProduct(String code, String name) {
        return Product.create(SYSTEM_TENANT_ID, code, name, null, Money.of("10.00", "CNY"));
    }

    @Test
    void wiredBeanIsJooqAdapter() {
        assertThat(repo).isSameAs(jooqAdapter);
    }

    @Test
    void shouldSaveAndFindById() {
        Product p = createProduct("SKU-" + UUID.randomUUID().toString().substring(0, 8), "Widget");
        ScopedValue.where(TenantContext.CURRENT, SYSTEM_TENANT_ID).run(() -> repo.save(p));

        Optional<Product> found = ScopedValue.where(TenantContext.CURRENT, SYSTEM_TENANT_ID)
                .call(() -> repo.findById(p.id()));

        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(p.id());
        assertThat(found.get().tenantId()).isEqualTo(SYSTEM_TENANT_ID);
        assertThat(found.get().code()).isEqualTo(p.code());
        assertThat(found.get().name()).isEqualTo(p.name());
        assertThat(found.get().price().amount()).isEqualByComparingTo("10.00");
        assertThat(found.get().status()).isEqualTo("ACTIVE");
    }

    @Test
    void findByIdMissReturnsEmpty() {
        Optional<Product> result = ScopedValue.where(TenantContext.CURRENT, SYSTEM_TENANT_ID)
                .call(() -> repo.findById(ProductId.newId()));
        assertThat(result).isEmpty();
    }

    @Test
    void shouldSaveIsUpsert_updateStatusOnSecondSave() {
        String code = "UPSERT-" + UUID.randomUUID().toString().substring(0, 8);
        Product p = createProduct(code, "Original");
        ScopedValue.where(TenantContext.CURRENT, SYSTEM_TENANT_ID).run(() -> repo.save(p));

        Product updated = p.updateDetails("Updated", "desc", Money.of("20.00", "CNY"));
        ScopedValue.where(TenantContext.CURRENT, SYSTEM_TENANT_ID).run(() -> repo.save(updated));

        Product found = ScopedValue.where(TenantContext.CURRENT, SYSTEM_TENANT_ID)
                .call(() -> repo.findById(p.id())).orElseThrow();
        assertThat(found.name()).isEqualTo("Updated");
        assertThat(found.price().amount()).isEqualByComparingTo("20.00");
    }

    @Test
    void shouldListActiveExcludesInactive() {
        String prefix = "list-" + UUID.randomUUID().toString().substring(0, 8);
        Product active = createProduct(prefix + "-ACT", "Active Product");
        Product inactive = createProduct(prefix + "-INACT", "Inactive Product");

        ScopedValue.where(TenantContext.CURRENT, SYSTEM_TENANT_ID).run(() -> {
            repo.save(active);
            repo.save(inactive.deactivate());
        });

        PageResult<Product> result = ScopedValue.where(TenantContext.CURRENT, SYSTEM_TENANT_ID)
                .call(() -> repo.listActive(new PageQuery(1, 200, null)));

        assertThat(result.items().stream().anyMatch(p -> p.id().equals(active.id()))).isTrue();
        assertThat(result.items().stream().noneMatch(p -> p.id().equals(inactive.id()))).isTrue();
        assertThat(result.items().stream().noneMatch(p -> "INACTIVE".equals(p.status()))).isTrue();
    }

    @Test
    void shouldPaginateCorrectly() {
        String prefix = "page-" + UUID.randomUUID().toString().substring(0, 8);
        Product p1 = createProduct(prefix + "-1", "Product 1");
        Product p2 = createProduct(prefix + "-2", "Product 2");
        Product p3 = createProduct(prefix + "-3", "Product 3");

        ScopedValue.where(TenantContext.CURRENT, SYSTEM_TENANT_ID).run(() -> {
            repo.save(p1);
            repo.save(p2);
            repo.save(p3);
        });

        // Total active is at least 3; page with size 1
        PageResult<Product> page1 = ScopedValue.where(TenantContext.CURRENT, SYSTEM_TENANT_ID)
                .call(() -> repo.listActive(new PageQuery(1, 1, null)));
        PageResult<Product> page2 = ScopedValue.where(TenantContext.CURRENT, SYSTEM_TENANT_ID)
                .call(() -> repo.listActive(new PageQuery(2, 1, null)));

        assertThat(page1.items()).hasSize(1);
        assertThat(page2.items()).hasSize(1);
        assertThat(page1.total()).isGreaterThanOrEqualTo(3L);
    }
}
