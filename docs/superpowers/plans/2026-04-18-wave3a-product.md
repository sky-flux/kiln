# Product Module — Wave 3a Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans`. Steps use `- [ ]` syntax.

**Goal:** Implement the `product` Gradle module — a Core Domain (Hexagonal/Ports & Adapters layout) providing a tenant-scoped product catalogue with CRUD endpoints and ArchUnit-enforced dependency rules.

**Architecture:** Core Domain → strict Hexagonal layout (same shape as `user` module). `Product` aggregate root with `ProductId` (UUID v7), `tenantId`, `code`, `name`, `description`, `price` (Money from `common`), `status` (ACTIVE/INACTIVE). Database First: `V10__product.sql` + jOOQ generated types. ArchUnit test mirrors `user/ArchitectureTest`. `TenantContext.CURRENT` bound by TenantFilter — jOOQ `SET LOCAL app.tenant_id` via `TenantRlsListener` scopes all queries automatically.

**Tech Stack:** Java 25, Spring Boot 4, jOOQ 3.20, ArchUnit, `common.money.Money`, `common.util.Ids`.

**Prerequisites:**
- Wave 1 complete (tenant module, RLS, UUID v7).
- Wave 2a/2b do NOT need to complete before Wave 3a.
- `./gradlew check` all green before starting.

---

## File Map

### New Gradle module: `product/`
```
product/
├── build.gradle
└── src/
    └── main/java/com/skyflux/kiln/product/
        ├── package-info.java
        ├── domain/
        │   ├── model/Product.java
        │   ├── model/ProductId.java
        │   ├── event/ProductEvent.java          (sealed interface + records)
        │   └── exception/ProductNotFoundException.java
        ├── application/
        │   ├── port/in/CreateProductUseCase.java
        │   ├── port/in/UpdateProductUseCase.java
        │   ├── port/in/DeleteProductUseCase.java
        │   ├── port/in/GetProductUseCase.java
        │   ├── port/in/ListProductsUseCase.java
        │   ├── port/out/ProductRepository.java
        │   └── usecase/ (impls)
        ├── adapter/
        │   ├── in/web/ProductController.java
        │   └── out/persistence/ProductJooqRepositoryAdapter.java
        │   └── out/persistence/ProductMapper.java
        └── config/ProductModuleConfig.java
    └── test/java/com/skyflux/kiln/product/
        ├── ArchitectureTest.java
        ├── domain/model/ProductTest.java
        ├── application/usecase/ (unit tests)
        └── adapter/out/persistence/ProductJooqRepositoryAdapterTest.java
```

### New migration
| File | Change |
|------|--------|
| `infra/src/main/resources/db/migration/V10__product.sql` | `products` table |
| `infra/src/main/resources/db/migration/R__rls.sql` | Append `products` RLS policy |

### Modified files
| File | Change |
|------|--------|
| `settings.gradle` | Add `include 'product'` |
| `app/build.gradle` | Add `implementation project(':product')` |
| `infra/build.gradle` | Add V10 to jOOQ scripts list |

---

## Task 1 — Flyway migration

- [ ] Create `V10__product.sql`:
```sql
-- V10__product.sql
CREATE TABLE products (
    id          UUID            PRIMARY KEY,
    tenant_id   UUID            NOT NULL REFERENCES tenants(id),
    code        VARCHAR(100)    NOT NULL,
    name        VARCHAR(200)    NOT NULL CHECK (length(trim(name)) > 0),
    description TEXT,
    price_amount NUMERIC(19, 4) NOT NULL CHECK (price_amount >= 0),
    price_currency VARCHAR(3)  NOT NULL DEFAULT 'CNY',
    status      VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT products_code_tenant_unique UNIQUE (code, tenant_id)
);
COMMENT ON TABLE products IS 'Product catalogue. Tenant-scoped via RLS.';
```

- [ ] Append to `R__rls.sql`:
```sql
-- products: isolate by tenant_id
ALTER TABLE products ENABLE ROW LEVEL SECURITY;
ALTER TABLE products FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON products;
CREATE POLICY tenant_isolation ON products
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
```

- [ ] Add `V10__product.sql` to jOOQ scripts list in `infra/build.gradle`.

- [ ] Regenerate: `./gradlew :infra:generateJooq`. Expect `Tables.PRODUCTS` + `ProductsRecord`.

- [ ] Commit: `git commit -m "✨ add products table with tenant RLS"`

---

## Task 2 — Scaffold product Gradle module

- [ ] Add `include 'product'` to `settings.gradle`.

- [ ] Create `product/build.gradle`:
```groovy
dependencies {
    implementation project(':common')
    implementation project(':infra')
    implementation project(':tenant')
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.modulith:spring-modulith-starter-core'
    implementation 'cn.dev33:sa-token-spring-boot3-starter:1.45.0'

    testImplementation 'org.springframework.boot:spring-boot-webmvc-test'
    testImplementation 'org.springframework.boot:spring-boot-testcontainers'
    testImplementation 'org.testcontainers:junit-jupiter:1.21.3'
    testImplementation 'org.testcontainers:postgresql:1.21.3'
    testImplementation 'org.springframework.boot:spring-boot-starter-flyway'
    testImplementation 'com.tngtech.archunit:archunit-junit5:1.3.0'
}

jacocoTestCoverageVerification {
    violationRules {
        rule {
            element = 'BUNDLE'
            limit { counter = 'LINE'; minimum = 0.70 }
            limit { counter = 'BRANCH'; minimum = 0.60 }
        }
    }
}
check.dependsOn jacocoTestCoverageVerification
```

- [ ] Add `implementation project(':product')` to `app/build.gradle`.

- [ ] Create `package-info.java`:
```java
@org.springframework.modulith.ApplicationModule(
    displayName = "Product",
    allowedDependencies = {"common", "infra", "tenant"}
)
package com.skyflux.kiln.product;
```

- [ ] Commit: `git commit -m "✨ scaffold product Gradle module"`

---

## Task 3 — ArchUnit test (write FIRST — it will fail until structure exists)

- [ ] Create `ArchitectureTest.java` — copy from `user/src/test/java/com/skyflux/kiln/user/ArchitectureTest.java` and change the package prefix to `com.skyflux.kiln.product`.

- [ ] Run: `./gradlew :product:test --tests 'com.skyflux.kiln.product.ArchitectureTest'`
  Expected: FAIL (no classes exist yet — ArchUnit reports no violation but also nothing to check; it may pass vacuously. Note any ArchUnit warnings).

---

## Task 4 — ProductId + Product domain model (TDD)

- [ ] Write failing `ProductTest`:
```java
class ProductTest {
    // Money uses java.util.Currency — use Money.of(amount, currencyCode) factory.
    // DB stores currency as VARCHAR(3) code; reconstruct via Currency.getInstance(code).

    @Test void shouldCreateProductWithActiveStatus() {
        UUID tenantId = Ids.next();
        Product p = Product.create(tenantId, "SKU-001", "Widget", null,
            Money.of("99.99", "CNY"));
        assertThat(p.status()).isEqualTo("ACTIVE");
        assertThat(p.id().value().version()).isEqualTo(7);
    }

    @Test void shouldDeactivateProduct() {
        Product p = Product.create(Ids.next(), "SKU-001", "Widget", null, Money.of("10.00", "CNY"));
        Product inactive = p.deactivate();
        assertThat(inactive.status()).isEqualTo("INACTIVE");
    }

    @Test void shouldRejectNegativePrice() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            Product.create(Ids.next(), "X", "X", null, Money.of("-1.00", "CNY")));
    }
}
```

- [ ] Create `ProductId.java`:
```java
package com.skyflux.kiln.product.domain.model;
import com.skyflux.kiln.common.util.Ids;
import java.util.Objects; import java.util.UUID;

public record ProductId(UUID value) {
    public ProductId { Objects.requireNonNull(value, "value"); }
    public static ProductId newId() { return new ProductId(Ids.next()); }
    public static ProductId of(String s) { return new ProductId(UUID.fromString(s)); }
}
```

- [ ] Create `Product.java` (domain aggregate, NO framework imports):
```java
package com.skyflux.kiln.product.domain.model;

import com.skyflux.kiln.common.money.Money;
import java.util.Objects;
import java.util.UUID;

public final class Product {
    private final ProductId id;
    private final UUID tenantId;
    private final String code;
    private final String name;
    private final String description;
    private final Money price;
    private final String status;

    private Product(ProductId id, UUID tenantId, String code, String name,
                    String description, Money price, String status) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.code = Objects.requireNonNull(code);
        this.name = Objects.requireNonNull(name);
        this.description = description;
        this.price = Objects.requireNonNull(price);
        this.status = Objects.requireNonNull(status);
        if (code.isBlank()) throw new IllegalArgumentException("code blank");
        if (name.isBlank()) throw new IllegalArgumentException("name blank");
        if (price.amount().signum() < 0) throw new IllegalArgumentException("price negative");
    }

    public static Product create(UUID tenantId, String code, String name,
                                  String description, Money price) {
        return new Product(ProductId.newId(), tenantId, code.trim(), name.trim(),
                           description, price, "ACTIVE");
    }

    public static Product reconstitute(ProductId id, UUID tenantId, String code, String name,
                                        String description, Money price, String status) {
        return new Product(id, tenantId, code, name, description, price, status);
    }

    public Product deactivate() {
        if ("INACTIVE".equals(status)) throw new IllegalStateException("Already inactive");
        return new Product(id, tenantId, code, name, description, price, "INACTIVE");
    }

    public Product updateDetails(String name, String description, Money price) {
        return new Product(id, tenantId, code, name.trim(), description, price, status);
    }

    // Accessors
    public ProductId id()         { return id; }
    public UUID tenantId()        { return tenantId; }
    public String code()          { return code; }
    public String name()          { return name; }
    public String description()   { return description; }
    public Money price()          { return price; }
    public String status()        { return status; }
}
```

- [ ] Run: `./gradlew :product:test --tests 'com.skyflux.kiln.product.domain.model.ProductTest'` — PASS.

- [ ] Commit: `git commit -m "✅ add ProductId and Product aggregate root"`

---

## Task 5 — Repository + persistence adapter (TDD)

- [ ] Create port `ProductRepository.java`:
```java
public interface ProductRepository {
    Optional<Product> findById(ProductId id);
    void save(Product product);
    void delete(ProductId id);
    PageResult<Product> listActive(PageQuery query);
}
```

- [ ] Write failing `ProductJooqRepositoryAdapterTest` (Testcontainers, `@DataJooqTest`):
```java
@Test void shouldSaveAndFindById() { ... }
@Test void shouldListActiveProductsTenantScoped() { ... }
@Test void shouldNotReturnInactiveProducts() { ... }
```

- [ ] Create `ProductMapper.java` + `ProductJooqRepositoryAdapter.java` following `UserMapper` / `UserJooqRepositoryAdapter` as template — map `PRODUCTS` table columns to/from `Product` aggregate.

Key mapping note: `price_amount` (BigDecimal) + `price_currency` (String VARCHAR(3)) → `Money`. Use `new Money(record.getPriceAmount().setScale(Currency.getInstance(record.getPriceCurrency()).getDefaultFractionDigits(), RoundingMode.HALF_EVEN), Currency.getInstance(record.getPriceCurrency()))` when reading. When writing: `product.price().amount()` and `product.price().currency().getCurrencyCode()`.

- [ ] Run: `./gradlew :product:test --tests '...ProductJooqRepositoryAdapterTest'` — PASS.

- [ ] Commit: `git commit -m "✅ add ProductJooqRepositoryAdapter with Testcontainers tests"`

---

## Task 6 — Use cases (TDD)

For each use case, write failing test first, then implement.

**Use cases:**
- `CreateProductUseCase` + `CreateProductService` — reads `TenantContext.CURRENT.get()`, checks code uniqueness, calls `repo.save`.
- `GetProductUseCase` + `GetProductService` — finds by ID, throws `NOT_FOUND`.
- `UpdateProductUseCase` + `UpdateProductService` — loads, calls `product.updateDetails()`, saves.
- `DeleteProductUseCase` + `DeleteProductService` — loads, calls `product.deactivate()`, saves.
- `ListProductsUseCase` + `ListProductsService` — paginates active products.

All service tests: `@ExtendWith(MockitoExtension.class)`, mock `ProductRepository`.

`CreateProductService.execute()` must read tenant from `TenantContext.CURRENT`:
```java
UUID tenantId = TenantContext.CURRENT.get();
Product p = Product.create(tenantId, cmd.code(), cmd.name(), cmd.description(), cmd.price());
repo.save(p);
```

- [ ] Implement all 5 services with TDD.

- [ ] Run: `./gradlew :product:test` — all green.

- [ ] Commit: `git commit -m "✅ add Product use cases (Create, Get, Update, Delete, List)"`

---

## Task 7 — REST controller (TDD)

- [ ] Create `ProductController.java`:
```
POST   /api/v1/products         @SaCheckLogin
GET    /api/v1/products         @SaCheckLogin (paginated)
GET    /api/v1/products/{id}    @SaCheckLogin
PUT    /api/v1/products/{id}    @SaCheckLogin
DELETE /api/v1/products/{id}    @SaCheckRole("ADMIN")
```

Request/Response records (flat in controller file since <5 DTOs):
```java
record CreateProductRequest(
    @NotBlank @Size(max = 100) String code,
    @NotBlank @Size(max = 200) String name,
    String description,
    @NotNull @DecimalMin("0") BigDecimal priceAmount,
    @NotBlank @Size(max = 3) String priceCurrency) {}

record UpdateProductRequest(
    @NotBlank @Size(max = 200) String name,
    String description,
    @NotNull @DecimalMin("0") BigDecimal priceAmount,
    @NotBlank @Size(max = 3) String priceCurrency) {}

record ProductResponse(String id, String code, String name, String description,
                       BigDecimal priceAmount, String priceCurrency, String status) {
    static ProductResponse from(Product p) { ... }
}
```

- [ ] Write `ProductControllerTest` using `@WebMvcTest(ProductController.class)` with mocked use cases.

- [ ] Run: `./gradlew :product:test` — all green.

- [ ] Run ArchUnit: `./gradlew :product:test --tests '...ArchitectureTest'` — must pass.

- [ ] Run: `./gradlew check` — all modules green.

- [ ] Run code review: invoke `superpowers:requesting-code-review`. Fix all findings.

- [ ] Commit:
```bash
git add .
git commit -m "✨ implement Product module: Core Domain hexagonal with tenant-scoped catalogue"
```

- [ ] Update OpenAPI snapshot: `./gradlew :app:updateOpenApiSnapshot` then commit.
