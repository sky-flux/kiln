# Tenant Module — Wave 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to implement this plan task-by-task. Steps use `- [ ]` syntax for tracking.

**Goal:** Implement the foundational multi-tenant infrastructure: PostgreSQL RLS isolation, Java 25 ScopedValue-based tenant context, UUID v7 IDs, and an admin CRUD API for tenant management — making all subsequent business modules tenant-scoped with zero per-query boilerplate.

**Architecture:** Generic subdomain (simplified layout: `api/domain/repo/internal/config`). `tenants` table is a *global catalogue* with **no RLS** (required for login-time lookup by code before a session exists). All business tables carry `tenant_id UUID NOT NULL` with PostgreSQL RLS policy `USING (tenant_id = current_setting('app.tenant_id', true)::uuid)`. Tenant context flows per-request: `TenantFilter` (Servlet filter, order=1) reads tenantId from Sa-Token session (authenticated) or `X-Tenant-Code` header (unauthenticated), binds `TenantContext.CURRENT` (`ScopedValue<UUID>`); `TenantRlsListener` (jOOQ `ExecuteListener`) executes `SET LOCAL app.tenant_id = ?` via raw JDBC before each statement. `user` module gains a `tenantId` field and depends on `tenant` for `TenantContext`.

**Tech Stack:** Java 25 `ScopedValue`, Spring Boot 4, jOOQ 3.20 `ExecuteListener`, Flyway, PostgreSQL RLS, `uuid-creator:6.0.0`, Sa-Token `SaLoginModel.setExtra`.

**Prerequisite:** Before starting, run `./gradlew test` — all existing tests must be green. If not, stop and fix.

---

## File Map

### New module `tenant/`
| File | Role |
|------|------|
| `tenant/build.gradle` | Module deps: `common`, `infra` |
| `tenant/src/main/java/com/skyflux/kiln/tenant/package-info.java` | `@ApplicationModule` |
| `tenant/src/main/java/com/skyflux/kiln/tenant/api/TenantContext.java` | `ScopedValue<UUID>` holder — public |
| `tenant/src/main/java/com/skyflux/kiln/tenant/api/TenantId.java` | Strongly-typed value object — public |
| `tenant/src/main/java/com/skyflux/kiln/tenant/domain/Tenant.java` | Domain record |
| `tenant/src/main/java/com/skyflux/kiln/tenant/repo/TenantJooqRepository.java` | jOOQ CRUD + `findByCode` |
| `tenant/src/main/java/com/skyflux/kiln/tenant/internal/TenantService.java` | CRUD use cases |
| `tenant/src/main/java/com/skyflux/kiln/tenant/internal/TenantController.java` | Admin REST (`@SaCheckRole("ADMIN")`) |
| `tenant/src/main/java/com/skyflux/kiln/tenant/config/TenantFilter.java` | Servlet filter, binds ScopedValue |
| `tenant/src/main/java/com/skyflux/kiln/tenant/config/TenantRlsListener.java` | jOOQ `ExecuteListener` |
| `tenant/src/main/java/com/skyflux/kiln/tenant/config/TenantModuleConfig.java` | `@Configuration` |

### New tests
| File | Role |
|------|------|
| `tenant/src/test/java/com/skyflux/kiln/tenant/repo/TenantJooqRepositoryTest.java` | `@DataJooqTest` + Testcontainers |
| `tenant/src/test/java/com/skyflux/kiln/tenant/internal/TenantServiceTest.java` | Pure unit test, mock repo |
| `tenant/src/test/java/com/skyflux/kiln/tenant/config/TenantFilterTest.java` | Mock servlet filter test |

### Modified files
| File | Change |
|------|--------|
| `settings.gradle` | Add `include 'tenant'` |
| `app/build.gradle` | Add `implementation project(':tenant')` |
| `common/build.gradle` | Add `implementation 'com.github.f4b6a3:uuid-creator:6.0.0'` |
| `common/src/main/java/com/skyflux/kiln/common/util/Ids.java` | NEW — UUID v7 factory |
| `user/src/main/java/com/skyflux/kiln/user/domain/model/UserId.java` | `newId()` → `Ids.next()` |
| `user/src/main/java/com/skyflux/kiln/user/domain/model/User.java` | Add `tenantId UUID` field |
| `user/src/main/java/com/skyflux/kiln/user/adapter/out/persistence/UserMapper.java` | Map `tenant_id` column |
| `user/src/main/java/com/skyflux/kiln/user/adapter/out/persistence/UserJooqRepositoryAdapter.java` | Include `tenant_id` in UPSERT |
| `user/src/main/java/com/skyflux/kiln/user/application/usecase/RegisterUserService.java` | Read `TenantContext.CURRENT` |
| `user/src/main/java/com/skyflux/kiln/user/application/usecase/AuthenticateUserService.java` | Store `tenantId` in Sa-Token session |
| `user/build.gradle` | Add `implementation project(':tenant')` |
| `infra/build.gradle` | jOOQ `scripts` property: exclude `R__*.sql` |
| `infra/src/main/resources/db/migration/V7__tenant.sql` | NEW — tenants table + users.tenant_id |
| `infra/src/main/resources/db/migration/R__rls.sql` | NEW — PostgreSQL RLS policies (not parsed by jOOQ H2) |
| `app/src/test/java/com/skyflux/kiln/KilnIntegrationTest.java` | Fix tenant context in existing tests; add RLS isolation test |
| Tests in `user/` | Update test fixtures with `tenantId` |

---

## Task 1 — UUID v7 factory in `common`

**Files:**
- Modify: `common/build.gradle`
- Create: `common/src/main/java/com/skyflux/kiln/common/util/Ids.java`
- Create: `common/src/test/java/com/skyflux/kiln/common/util/IdsTest.java`

- [ ] Write failing test:
```java
// common/src/test/java/com/skyflux/kiln/common/util/IdsTest.java
package com.skyflux.kiln.common.util;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class IdsTest {
    @Test void shouldGenerateUuidV7() {
        assertThat(Ids.next().version()).isEqualTo(7);
    }
    @Test void shouldGenerateUniqueIds() {
        assertThat(Ids.next()).isNotEqualTo(Ids.next());
    }
    @Test void shouldBeTimeOrdered() {
        UUID a = Ids.next(); UUID b = Ids.next();
        assertThat(a.compareTo(b)).isLessThan(0);
    }
}
```

- [ ] Run to confirm FAIL: `./gradlew :common:test --tests 'com.skyflux.kiln.common.util.IdsTest'`

- [ ] Add to `common/build.gradle` (inside `dependencies {}`):
```groovy
implementation 'com.github.f4b6a3:uuid-creator:6.0.0'
```

- [ ] Create `Ids.java`:
```java
package com.skyflux.kiln.common.util;

import com.github.f4b6a3.uuid.UuidCreator;
import java.util.UUID;

/** Factory for time-ordered UUID v7 identifiers. Use instead of {@code UUID.randomUUID()}. */
public final class Ids {
    private Ids() {}
    public static UUID next() { return UuidCreator.getTimeOrderedEpoch(); }
}
```

- [ ] Run to confirm PASS: `./gradlew :common:test --tests 'com.skyflux.kiln.common.util.IdsTest'`

- [ ] Update `UserId.newId()` in `user/src/main/java/com/skyflux/kiln/user/domain/model/UserId.java`:
```java
import com.skyflux.kiln.common.util.Ids;
// ...
public static UserId newId() { return new UserId(Ids.next()); }
```

- [ ] Run: `./gradlew :common:test :user:test` — all green.

- [ ] Commit:
```bash
git add common/ user/src/main/java/com/skyflux/kiln/user/domain/model/UserId.java
git commit -m "✨ add UUID v7 Ids factory; switch UserId.newId() to time-ordered UUIDs"
```

---

## Task 2 — Flyway migration: tenants table + users.tenant_id

**Files:**
- Create: `infra/src/main/resources/db/migration/V7__tenant.sql`
- Create: `infra/src/main/resources/db/migration/R__rls.sql`
- Modify: `infra/build.gradle`

- [ ] Create `V7__tenant.sql` (H2-compatible DDL only):
```sql
-- V7__tenant.sql
-- Creates the tenants catalogue and adds tenant_id to users.
-- RLS policies are in R__rls.sql (excluded from jOOQ DDLDatabase — H2 can't parse them).

CREATE TABLE tenants (
    id         UUID         PRIMARY KEY,
    code       VARCHAR(50)  NOT NULL UNIQUE,
    name       VARCHAR(200) NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT tenants_code_lower CHECK (code = lower(code))
);
COMMENT ON TABLE  tenants      IS 'Tenant catalogue. No RLS — needed for pre-auth lookup by code.';
COMMENT ON COLUMN tenants.code IS 'Lowercase slug, e.g. acme-corp. Used in X-Tenant-Code header.';

-- Seed the system tenant. Fixed UUID v7-format value so migrations are idempotent.
INSERT INTO tenants (id, code, name, status)
VALUES ('00000000-0000-7000-8000-000000000001', 'system', 'System Tenant', 'ACTIVE')
ON CONFLICT DO NOTHING;

-- Add tenant_id to users; back-fill all existing users to system tenant.
ALTER TABLE users ADD COLUMN tenant_id UUID REFERENCES tenants(id);
UPDATE users SET tenant_id = '00000000-0000-7000-8000-000000000001';
ALTER TABLE users ALTER COLUMN tenant_id SET NOT NULL;

-- Email uniqueness is now per-tenant (two tenants can have the same email).
-- Drop the old global unique constraint (created in V1 as users_email_key).
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_key;
ALTER TABLE users ADD CONSTRAINT users_email_tenant_unique UNIQUE (email, tenant_id);
COMMENT ON COLUMN users.tenant_id IS 'Owning tenant. Inherited by all queries via PostgreSQL RLS.';
```

- [ ] Create `R__rls.sql` (PostgreSQL-specific; excluded from jOOQ codegen):
```sql
-- R__rls.sql
-- PostgreSQL Row-Level Security policies.
-- Flyway applies this after V__ migrations. jOOQ DDLDatabase does NOT read R__ scripts
-- (H2 can't parse ENABLE ROW LEVEL SECURITY / CREATE POLICY).
-- SET LOCAL app.tenant_id is set by TenantRlsListener before each jOOQ statement.

-- tenants: NO RLS — the filter reads tenants by code before session exists.

-- users: isolate by tenant_id.
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE users FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON users;
CREATE POLICY tenant_isolation ON users
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
```

- [ ] Update `infra/build.gradle` jOOQ scripts property to exclude `R__` files.
  Replace the existing `property { key = 'scripts'; ... }` block with:
```groovy
property {
    key = 'scripts'
    // R__*.sql contains PG-specific RLS DDL that H2's DDLDatabase cannot parse.
    // Enumerate V migrations explicitly; add new Vxx files here as they land.
    value = [
        'V1__init_schema.sql',
        'V2__email_lowercase_check.sql',
        'V3__users_password_hash.sql',
        'V4__rbac.sql',
        'V5__audit_events.sql',
        'V6__users_lockout.sql',
        'V7__tenant.sql',
    ].collect { "${project.projectDir}/src/main/resources/db/migration/${it}" }.join(',')
}
```

- [ ] Regenerate jOOQ:
```bash
./gradlew :infra:generateJooq
```
Expected: SUCCESS. The generated `UsersRecord` should now have a `getTenantId()` / `setTenantId(UUID)` method.

- [ ] Verify: `grep -r "getTenantId" infra/build/generated-src/jooq/main` — should find it in `UsersRecord.java`.

- [ ] Commit:
```bash
git add infra/
git commit -m "✨ add tenants table, users.tenant_id, PostgreSQL RLS; exclude R__ from jOOQ codegen"
```

---

## Task 3 — Scaffold tenant Gradle module

**Files:**
- Modify: `settings.gradle`
- Create: `tenant/build.gradle`
- Modify: `app/build.gradle`
- Modify: `user/build.gradle`
- Create: `tenant/src/main/java/com/skyflux/kiln/tenant/package-info.java`

- [ ] Add to `settings.gradle` (after `include 'user'`):
```groovy
include 'tenant'
```

- [ ] Create `tenant/build.gradle`:
```groovy
dependencies {
    implementation project(':common')
    implementation project(':infra')
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.modulith:spring-modulith-starter-core'
    implementation 'cn.dev33:sa-token-spring-boot3-starter:1.45.0'

    testImplementation 'org.springframework.boot:spring-boot-webmvc-test'
    testImplementation 'org.springframework.boot:spring-boot-testcontainers'
    testImplementation 'org.testcontainers:junit-jupiter:1.21.3'
    testImplementation 'org.testcontainers:postgresql:1.21.3'
    testImplementation 'org.springframework.boot:spring-boot-starter-flyway'
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

- [ ] Add to `app/build.gradle` dependencies:
```groovy
implementation project(':tenant')
```

- [ ] Add to `user/build.gradle` dependencies:
```groovy
implementation project(':tenant')
```

- [ ] Create package-info:
```java
// tenant/src/main/java/com/skyflux/kiln/tenant/package-info.java
@org.springframework.modulith.ApplicationModule(
    displayName = "Tenant",
    allowedDependencies = {"common", "infra"}
)
package com.skyflux.kiln.tenant;
```

- [ ] Verify build resolves: `./gradlew :tenant:compileJava` — should succeed with empty module.

- [ ] Commit:
```bash
git add settings.gradle tenant/ app/build.gradle user/build.gradle
git commit -m "✨ scaffold tenant Gradle module with dependencies"
```

---

## Task 4 — TenantId value object + TenantContext (TDD)

**Files:**
- Create: `tenant/src/main/java/com/skyflux/kiln/tenant/api/TenantId.java`
- Create: `tenant/src/main/java/com/skyflux/kiln/tenant/api/TenantContext.java`
- Create: `tenant/src/test/java/com/skyflux/kiln/tenant/api/TenantIdTest.java`

- [ ] Write failing test:
```java
// tenant/src/test/java/com/skyflux/kiln/tenant/api/TenantIdTest.java
package com.skyflux.kiln.tenant.api;

import com.skyflux.kiln.common.util.Ids;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class TenantIdTest {
    @Test void shouldRejectNull() {
        assertThatNullPointerException().isThrownBy(() -> new TenantId(null));
    }
    @Test void shouldExposeValue() {
        UUID uuid = Ids.next();
        assertThat(new TenantId(uuid).value()).isEqualTo(uuid);
    }
    @Test void newIdShouldGenerateV7() {
        assertThat(TenantId.newId().value().version()).isEqualTo(7);
    }
    @Test void ofStringShouldParse() {
        UUID uuid = Ids.next();
        assertThat(TenantId.of(uuid.toString()).value()).isEqualTo(uuid);
    }
}
```

- [ ] Run: `./gradlew :tenant:test --tests 'com.skyflux.kiln.tenant.api.TenantIdTest'` — FAIL.

- [ ] Create `TenantId.java`:
```java
package com.skyflux.kiln.tenant.api;

import com.skyflux.kiln.common.util.Ids;
import java.util.Objects;
import java.util.UUID;

/** Strongly-typed tenant identifier. Public — consumed by user, product, order modules. */
public record TenantId(UUID value) {
    public TenantId { Objects.requireNonNull(value, "value"); }
    public static TenantId newId() { return new TenantId(Ids.next()); }
    public static TenantId of(String s) { return new TenantId(UUID.fromString(s)); }
}
```

- [ ] Create `TenantContext.java`:
```java
package com.skyflux.kiln.tenant.api;

import java.util.UUID;

/**
 * Holds the current request's tenant ID in a Java 25 ScopedValue.
 * Public — all business modules read CURRENT to scope their queries.
 * Bound per-request by {@code TenantFilter}; consumed by {@code TenantRlsListener}.
 */
public final class TenantContext {
    public static final ScopedValue<UUID> CURRENT = ScopedValue.newInstance();
    private TenantContext() {}
}
```

- [ ] Run: `./gradlew :tenant:test --tests 'com.skyflux.kiln.tenant.api.TenantIdTest'` — PASS.

- [ ] Commit:
```bash
git add tenant/src/
git commit -m "✅ add TenantId value object and TenantContext ScopedValue"
```

---

## Task 5 — Tenant domain model + repository (TDD)

**Files:**
- Create: `tenant/src/main/java/com/skyflux/kiln/tenant/domain/Tenant.java`
- Create: `tenant/src/main/java/com/skyflux/kiln/tenant/repo/TenantJooqRepository.java`
- Create: `tenant/src/test/java/com/skyflux/kiln/tenant/repo/TenantJooqRepositoryTest.java`

- [ ] Create domain model:
```java
package com.skyflux.kiln.tenant.domain;

import com.skyflux.kiln.tenant.api.TenantId;
import java.time.Instant;

/** Tenant aggregate — immutable record. */
public record Tenant(
    TenantId id,
    String code,
    String name,
    String status,
    Instant createdAt
) {
    public boolean isActive() { return "ACTIVE".equals(status); }
}
```

- [ ] Write failing repository test (`@DataJooqTest` + Testcontainers):
```java
// tenant/src/test/java/com/skyflux/kiln/tenant/repo/TenantJooqRepositoryTest.java
package com.skyflux.kiln.tenant.repo;

import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.tenant.api.TenantId;
import com.skyflux.kiln.tenant.domain.Tenant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jooq.autoconfigure.JooqAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jooq.DataJooqTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJooqTest
@Testcontainers
@ImportAutoConfiguration({FlywayAutoConfiguration.class, DataSourceAutoConfiguration.class, JooqAutoConfiguration.class})
class TenantJooqRepositoryTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:18.3");

    @Autowired TenantJooqRepository repo;

    @Test void shouldSaveAndFindById() {
        TenantId id = TenantId.newId();
        Tenant t = new Tenant(id, "acme", "ACME Corp", "ACTIVE", null);
        repo.save(t);
        assertThat(repo.findById(id)).isPresent()
            .get().extracting(Tenant::code).isEqualTo("acme");
    }

    @Test void shouldFindByCode() {
        TenantId id = TenantId.newId();
        repo.save(new Tenant(id, "beta-co", "Beta Co", "ACTIVE", null));
        assertThat(repo.findByCode("beta-co")).isPresent();
    }

    @Test void shouldReturnEmptyForUnknownCode() {
        assertThat(repo.findByCode("no-such-tenant")).isEmpty();
    }

    @Test void shouldFindSystemTenantSeededByMigration() {
        // V7 seeds 'system' tenant
        assertThat(repo.findByCode("system")).isPresent();
    }
}
```

- [ ] Run: `./gradlew :tenant:test --tests 'com.skyflux.kiln.tenant.repo.TenantJooqRepositoryTest'` — FAIL (class not found).

- [ ] Create `TenantJooqRepository.java`:
```java
package com.skyflux.kiln.tenant.repo;

import com.skyflux.kiln.infra.jooq.generated.Tables;
import com.skyflux.kiln.infra.jooq.generated.tables.records.TenantsRecord;
import com.skyflux.kiln.tenant.api.TenantId;
import com.skyflux.kiln.tenant.domain.Tenant;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

@Repository
public class TenantJooqRepository {

    private final DSLContext dsl;

    TenantJooqRepository(DSLContext dsl) { this.dsl = dsl; }

    public Optional<Tenant> findById(TenantId id) {
        Objects.requireNonNull(id, "id");
        return dsl.selectFrom(Tables.TENANTS)
                .where(Tables.TENANTS.ID.eq(id.value()))
                .fetchOptional()
                .map(this::toTenant);
    }

    /** Auth-path lookup — tenants table has no RLS, safe without tenant context. */
    public Optional<Tenant> findByCode(String code) {
        Objects.requireNonNull(code, "code");
        return dsl.selectFrom(Tables.TENANTS)
                .where(Tables.TENANTS.CODE.eq(code))
                .fetchOptional()
                .map(this::toTenant);
    }

    public void save(Tenant tenant) {
        Objects.requireNonNull(tenant, "tenant");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        dsl.insertInto(Tables.TENANTS)
                .set(Tables.TENANTS.ID, tenant.id().value())
                .set(Tables.TENANTS.CODE, tenant.code())
                .set(Tables.TENANTS.NAME, tenant.name())
                .set(Tables.TENANTS.STATUS, tenant.status())
                .set(Tables.TENANTS.CREATED_AT, now)
                .set(Tables.TENANTS.UPDATED_AT, now)
                .onConflict(Tables.TENANTS.ID)
                .doUpdate()
                .set(Tables.TENANTS.NAME, tenant.name())
                .set(Tables.TENANTS.STATUS, tenant.status())
                .set(Tables.TENANTS.UPDATED_AT, now)
                .execute();
    }

    private Tenant toTenant(TenantsRecord r) {
        return new Tenant(
                new TenantId(r.getId()),
                r.getCode(),
                r.getName(),
                r.getStatus(),
                r.getCreatedAt().toInstant());
    }
}
```

- [ ] Run: `./gradlew :tenant:test --tests 'com.skyflux.kiln.tenant.repo.TenantJooqRepositoryTest'` — PASS.

- [ ] Commit:
```bash
git add tenant/src/
git commit -m "✅ add Tenant domain model and TenantJooqRepository with Testcontainers tests"
```

---

## Task 6 — TenantService + TenantController (TDD)

**Files:**
- Create: `tenant/src/main/java/com/skyflux/kiln/tenant/internal/TenantService.java`
- Create: `tenant/src/main/java/com/skyflux/kiln/tenant/internal/TenantController.java`
- Create: `tenant/src/test/java/com/skyflux/kiln/tenant/internal/TenantServiceTest.java`

**Request/Response records (flat in `internal` package — <5 DTOs):**

```java
// In TenantController.java as inner records:
record CreateTenantRequest(
    @NotBlank @Size(max = 50) @Pattern(regexp = "[a-z0-9-]+") String code,
    @NotBlank @Size(max = 200) String name
) {}

record UpdateTenantRequest(
    @NotBlank @Size(max = 200) String name,
    @NotBlank String status
) {}

record TenantResponse(String id, String code, String name, String status, Instant createdAt) {
    static TenantResponse from(Tenant t) {
        return new TenantResponse(t.id().value().toString(), t.code(), t.name(), t.status(), t.createdAt());
    }
}
```

- [ ] Write failing TenantService unit test:
```java
// tenant/src/test/java/com/skyflux/kiln/tenant/internal/TenantServiceTest.java
package com.skyflux.kiln.tenant.internal;

import com.skyflux.kiln.tenant.domain.Tenant;
import com.skyflux.kiln.tenant.repo.TenantJooqRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock TenantJooqRepository repo;
    @InjectMocks TenantService service;

    @Test void shouldCreateTenant() {
        when(repo.findByCode("acme")).thenReturn(Optional.empty());
        var result = service.create("acme", "ACME Corp");
        assertThat(result.code()).isEqualTo("acme");
        assertThat(result.status()).isEqualTo("ACTIVE");
        verify(repo).save(any());
    }

    @Test void shouldRejectDuplicateCode() {
        when(repo.findByCode("acme")).thenReturn(Optional.of(
            new Tenant(null, "acme", "Existing", "ACTIVE", null)));
        assertThatThrownBy(() -> service.create("acme", "Duplicate"))
            .hasMessageContaining("already exists");
    }

    @Test void shouldSuspendTenant() {
        var id = com.skyflux.kiln.tenant.api.TenantId.newId();
        var existing = new Tenant(id, "acme", "ACME", "ACTIVE", null);
        when(repo.findById(id)).thenReturn(Optional.of(existing));
        service.suspend(id);
        verify(repo).save(argThat(t -> "SUSPENDED".equals(t.status())));
    }
}
```

- [ ] Run: `./gradlew :tenant:test --tests 'com.skyflux.kiln.tenant.internal.TenantServiceTest'` — FAIL.

- [ ] Create `TenantService.java`:
```java
package com.skyflux.kiln.tenant.internal;

import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.tenant.api.TenantId;
import com.skyflux.kiln.tenant.domain.Tenant;
import com.skyflux.kiln.tenant.repo.TenantJooqRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
class TenantService {

    private final TenantJooqRepository repo;

    TenantService(TenantJooqRepository repo) { this.repo = repo; }

    @Transactional
    public Tenant create(String code, String name) {
        Objects.requireNonNull(code); Objects.requireNonNull(name);
        if (repo.findByCode(code).isPresent()) {
            throw new AppException(AppCode.CONFLICT, "Tenant code already exists: " + code);
        }
        Tenant t = new Tenant(TenantId.newId(), code, name, "ACTIVE", null);
        repo.save(t);
        return t;
    }

    @Transactional
    public Tenant update(TenantId id, String name, String status) {
        Tenant existing = repo.findById(id)
            .orElseThrow(() -> new AppException(AppCode.NOT_FOUND));
        Tenant updated = new Tenant(id, existing.code(), name, status, existing.createdAt());
        repo.save(updated);
        return updated;
    }

    @Transactional
    public void suspend(TenantId id) {
        Tenant existing = repo.findById(id)
            .orElseThrow(() -> new AppException(AppCode.NOT_FOUND));
        repo.save(new Tenant(id, existing.code(), existing.name(), "SUSPENDED", existing.createdAt()));
    }

    public Tenant get(TenantId id) {
        return repo.findById(id).orElseThrow(() -> new AppException(AppCode.NOT_FOUND));
    }
}
```

- [ ] Run: `./gradlew :tenant:test --tests 'com.skyflux.kiln.tenant.internal.TenantServiceTest'` — PASS.

- [ ] Create `TenantController.java` (add `NOT_FOUND` to `AppCode` first if missing):
```java
package com.skyflux.kiln.tenant.internal;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.skyflux.kiln.tenant.api.TenantId;
import com.skyflux.kiln.tenant.domain.Tenant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/admin/tenants")
@SaCheckRole("ADMIN")
class TenantController {

    private final TenantService service;
    TenantController(TenantService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TenantResponse create(@Valid @RequestBody CreateTenantRequest req) {
        return TenantResponse.from(service.create(req.code(), req.name()));
    }

    @GetMapping("/{id}")
    TenantResponse get(@PathVariable String id) {
        return TenantResponse.from(service.get(TenantId.of(id)));
    }

    @PutMapping("/{id}")
    TenantResponse update(@PathVariable String id, @Valid @RequestBody UpdateTenantRequest req) {
        return TenantResponse.from(service.update(TenantId.of(id), req.name(), req.status()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void suspend(@PathVariable String id) {
        service.suspend(TenantId.of(id));
    }

    record CreateTenantRequest(
        @NotBlank @Size(max = 50) @Pattern(regexp = "[a-z0-9-]+") String code,
        @NotBlank @Size(max = 200) String name) {}

    record UpdateTenantRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank String status) {}

    record TenantResponse(String id, String code, String name, String status, Instant createdAt) {
        static TenantResponse from(Tenant t) {
            return new TenantResponse(
                t.id().value().toString(), t.code(), t.name(), t.status(), t.createdAt());
        }
    }
}
```

- [ ] Run: `./gradlew :tenant:test` — all green.

- [ ] Commit:
```bash
git add tenant/src/
git commit -m "✅ add TenantService CRUD and TenantController admin API"
```

---

## Task 7 — TenantFilter + TenantRlsListener (TDD)

**Files:**
- Create: `tenant/src/main/java/com/skyflux/kiln/tenant/config/TenantFilter.java`
- Create: `tenant/src/main/java/com/skyflux/kiln/tenant/config/TenantRlsListener.java`
- Create: `tenant/src/main/java/com/skyflux/kiln/tenant/config/TenantModuleConfig.java`
- Create: `tenant/src/test/java/com/skyflux/kiln/tenant/config/TenantFilterTest.java`

- [ ] Write failing TenantFilter unit test:
```java
// tenant/src/test/java/com/skyflux/kiln/tenant/config/TenantFilterTest.java
package com.skyflux.kiln.tenant.config;

import com.skyflux.kiln.tenant.api.TenantContext;
import com.skyflux.kiln.tenant.api.TenantId;
import com.skyflux.kiln.tenant.domain.Tenant;
import com.skyflux.kiln.tenant.repo.TenantJooqRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantFilterTest {

    @Mock TenantJooqRepository tenantRepo;
    @InjectMocks TenantFilter filter;

    @Test void shouldBindTenantContextFromHeader() throws Exception {
        TenantId tenantId = TenantId.newId();
        when(tenantRepo.findByCode("acme"))
            .thenReturn(Optional.of(new Tenant(tenantId, "acme", "ACME", "ACTIVE", null)));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Tenant-Code", "acme");
        AtomicReference<UUID> captured = new AtomicReference<>();
        FilterChain chain = (r, s) -> captured.set(TenantContext.CURRENT.get());

        filter.doFilterInternal(req, new MockHttpServletResponse(), chain);

        assertThat(captured.get()).isEqualTo(tenantId.value());
    }

    @Test void shouldPassThroughWithoutHeaderOnPublicRoute() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(req, any());
        verify(tenantRepo, never()).findByCode(any());
    }
}
```

- [ ] Run: `./gradlew :tenant:test --tests 'com.skyflux.kiln.tenant.config.TenantFilterTest'` — FAIL.

- [ ] Create `TenantFilter.java`:
```java
package com.skyflux.kiln.tenant.config;

import cn.dev33.satoken.stp.StpUtil;
import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.tenant.api.TenantContext;
import com.skyflux.kiln.tenant.repo.TenantJooqRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/** Binds TenantContext.CURRENT per request. Must run before business controllers. */
@Order(1)
class TenantFilter extends OncePerRequestFilter {

    private final TenantJooqRepository tenantRepo;

    TenantFilter(TenantJooqRepository tenantRepo) { this.tenantRepo = tenantRepo; }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        UUID tenantId = resolveTenantId(request);
        if (tenantId == null) {
            chain.doFilter(request, response);
            return;
        }
        runInTenantScope(tenantId, request, response, chain);
    }

    private UUID resolveTenantId(HttpServletRequest request) {
        // Authenticated requests: tenant stored in Sa-Token session
        try {
            if (StpUtil.isLogin()) {
                Object extra = StpUtil.getExtra("tenantId");
                if (extra != null) return UUID.fromString(extra.toString());
            }
        } catch (Exception ignored) {}

        // Unauthenticated: tenant from header (login, register flows)
        String code = request.getHeader("X-Tenant-Code");
        if (code == null) return null;  // public route — proceed without tenant context

        return tenantRepo.findByCode(code)
                .map(t -> t.id().value())
                .orElseThrow(() -> new AppException(AppCode.NOT_FOUND, "Unknown tenant: " + code));
    }

    private void runInTenantScope(UUID tenantId, HttpServletRequest req,
                                  HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        try {
            ScopedValue.where(TenantContext.CURRENT, tenantId).run(() -> {
                try {
                    chain.doFilter(req, res);
                } catch (Exception e) { throw new RuntimeException(e); }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioe) throw ioe;
            if (e.getCause() instanceof ServletException se) throw se;
            throw e;
        }
    }
}
```

- [ ] Create `TenantRlsListener.java`:
```java
package com.skyflux.kiln.tenant.config;

import com.skyflux.kiln.tenant.api.TenantContext;
import org.jooq.ExecuteContext;
import org.jooq.impl.DefaultExecuteListener;

import java.sql.SQLException;

/**
 * jOOQ ExecuteListener — sets PostgreSQL session variable before each statement.
 * Uses raw JDBC (not ctx.dsl()) to avoid infinite listener recursion.
 */
class TenantRlsListener extends DefaultExecuteListener {

    @Override
    public void executeStart(ExecuteContext ctx) {
        if (!TenantContext.CURRENT.isBound()) return;
        try {
            var conn = ctx.connection();
            try (var stmt = conn.prepareStatement("SET LOCAL app.tenant_id = ?")) {
                stmt.setString(1, TenantContext.CURRENT.get().toString());
                stmt.execute();
            }
        } catch (SQLException e) {
            throw new org.jooq.exception.DataAccessException("Failed to set tenant_id", e);
        }
    }
}
```

- [ ] Create `TenantModuleConfig.java`:
```java
package com.skyflux.kiln.tenant.config;

import com.skyflux.kiln.tenant.repo.TenantJooqRepository;
import org.jooq.ExecuteListenerProvider;
import org.jooq.impl.DefaultExecuteListenerProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class TenantModuleConfig {

    @Bean
    TenantFilter tenantFilter(TenantJooqRepository repo) {
        return new TenantFilter(repo);
    }

    @Bean
    ExecuteListenerProvider tenantRlsListenerProvider() {
        return new DefaultExecuteListenerProvider(new TenantRlsListener());
    }
}
```

- [ ] Run: `./gradlew :tenant:test --tests 'com.skyflux.kiln.tenant.config.TenantFilterTest'` — PASS.

- [ ] Run all tenant tests: `./gradlew :tenant:test` — all green.

- [ ] Commit:
```bash
git add tenant/src/
git commit -m "✅ add TenantFilter (ScopedValue) and TenantRlsListener (jOOQ SET LOCAL)"
```

---

## Task 8 — Update User domain to carry tenantId

**Files:**
- Modify: `user/src/main/java/com/skyflux/kiln/user/domain/model/User.java`
- Modify: `user/src/main/java/com/skyflux/kiln/user/adapter/out/persistence/UserMapper.java`
- Modify: `user/src/main/java/com/skyflux/kiln/user/adapter/out/persistence/UserJooqRepositoryAdapter.java`
- Modify: `user/src/main/java/com/skyflux/kiln/user/application/usecase/RegisterUserService.java`
- Modify: `user/src/main/java/com/skyflux/kiln/user/application/usecase/AuthenticateUserService.java`

**IMPORTANT — TDD rule:** Update tests BEFORE changing production code. The following test classes will need updates:
- `user/src/test/java/.../UserTest.java`
- `user/src/test/java/.../UserMapperTest.java`
- `user/src/test/java/.../UserJooqRepositoryAdapterTest.java`
- `user/src/test/java/.../RegisterUserServiceTest.java`
- `user/src/test/java/.../AuthenticateUserServiceTest.java`

- [ ] Read all existing tests in the `user` module first (`./gradlew :user:test` — note which ones exist and what they assert).

- [ ] Update `User.java` — add `tenantId UUID` field:
```java
public final class User {
    private final UserId id;
    private final UUID tenantId;   // NEW
    private final String name;
    private final String email;
    private final String passwordHash;
    private final int failedLoginAttempts;
    private final Instant lockedUntil;

    private User(UserId id, UUID tenantId, String name, String email,
                 String passwordHash, int failedLoginAttempts, Instant lockedUntil) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.name = Objects.requireNonNull(name, "name");
        this.email = Objects.requireNonNull(email, "email");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.failedLoginAttempts = failedLoginAttempts;
        this.lockedUntil = lockedUntil;
        if (name.isBlank()) throw new IllegalArgumentException("name blank");
        if (!email.contains("@")) throw new IllegalArgumentException("email invalid");
        if (passwordHash.isBlank()) throw new IllegalArgumentException("passwordHash blank");
    }

    /** Factory for new users. tenantId comes from TenantContext in the application layer. */
    public static User register(UUID tenantId, String name, String email, String passwordHash) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(email, "email");
        String normalizedName = name.trim();
        String normalizedEmail = email.trim().toLowerCase(java.util.Locale.ROOT);
        return new User(UserId.newId(), tenantId, normalizedName, normalizedEmail, passwordHash, 0, null);
    }

    public static User reconstitute(UserId id, UUID tenantId, String name, String email,
                                    String passwordHash, int failedLoginAttempts, Instant lockedUntil) {
        return new User(id, tenantId, name, email, passwordHash, failedLoginAttempts, lockedUntil);
    }

    public UserId id()                  { return id; }
    public UUID tenantId()              { return tenantId; }
    public String name()                { return name; }
    public String email()               { return email; }
    public String passwordHash()        { return passwordHash; }
    public int failedLoginAttempts()    { return failedLoginAttempts; }
    public Instant lockedUntil()        { return lockedUntil; }

    public boolean isLocked(Instant now) {
        Objects.requireNonNull(now, "now");
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    public User registerLoginFailure(Instant now, int lockThreshold, Duration lockDuration) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(lockDuration, "lockDuration");
        if (lockThreshold < 1) throw new IllegalArgumentException("lockThreshold must be \u2265 1");
        int next = failedLoginAttempts + 1;
        if (next >= lockThreshold) {
            return new User(id, tenantId, name, email, passwordHash, 0, now.plus(lockDuration));
        }
        return new User(id, tenantId, name, email, passwordHash, next, lockedUntil);
    }

    public User registerLoginSuccess() {
        return new User(id, tenantId, name, email, passwordHash, 0, null);
    }
}
```

- [ ] Update all `User.register(name, email, hash)` call sites to `User.register(tenantId, name, email, hash)`.

- [ ] Update `UserMapper.toAggregate()`:
```java
User toAggregate(UsersRecord record) {
    Integer failed = record.getFailedLoginAttempts();
    OffsetDateTime lockedUntil = record.getLockedUntil();
    return User.reconstitute(
            new UserId(record.getId()),
            record.getTenantId(),           // NEW
            record.getName(),
            record.getEmail(),
            record.getPasswordHash(),
            failed == null ? 0 : failed,
            lockedUntil == null ? null : lockedUntil.toInstant());
}
```

- [ ] Update `UserMapper.toRecord()` — add `record.setTenantId(user.tenantId())`.

- [ ] Update `UserJooqRepositoryAdapter.save()` — add `tenant_id` to DO UPDATE clause:
```java
.set(Tables.USERS.TENANT_ID, r.getTenantId())
```

- [ ] Update `RegisterUserService.execute()` — read from TenantContext:
```java
import com.skyflux.kiln.tenant.api.TenantContext;
// In execute():
UUID tenantId = TenantContext.CURRENT.get();
u = User.register(tenantId, cmd.name(), cmd.email(), hash);
```

- [ ] Update `AuthenticateUserService.execute()` — store tenantId in session:
```java
import cn.dev33.satoken.stp.SaLoginModel;
// After password verified, replace StpUtil.login(...) with:
SaLoginModel loginModel = new SaLoginModel()
    .setExtra("tenantId", user.tenantId().toString());
StpUtil.login(user.id().value().toString(), loginModel);
```

- [ ] Update all broken user module tests (add tenantId where User.register/reconstitute is called). Use `Ids.next()` for tenantId in test fixtures.

- [ ] Run: `./gradlew :user:test` — all green.

- [ ] Run: `./gradlew :tenant:test :user:test :auth:test` — all green.

- [ ] Commit:
```bash
git add user/ tenant/
git commit -m "✅ propagate tenantId through User aggregate, mapper, and repo; wire TenantContext in registration/login"
```

---

## Task 9 — Full integration: wire tenant module + RLS isolation test

- [ ] Run full build: `./gradlew build` — if anything fails, fix before proceeding.

- [ ] Start the app locally to verify context loads:
```bash
./gradlew :app:bootRun
```
Expected: app starts, no bean creation errors.

- [ ] Add/extend `KilnIntegrationTest` — verify RLS isolates tenants:

```java
@Test
void rlsIsolatesTenantData() throws Exception {
    // Create two tenants via admin API
    String adminToken = loginAsAdmin();
    String tenantAId = createTenant(adminToken, "tenant-a", "Tenant A");
    String tenantBId = createTenant(adminToken, "tenant-b", "Tenant B");

    // Register a user in tenant A
    restTestClient.post().uri("/api/v1/users")
        .header("X-Tenant-Code", "tenant-a")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("""{"name":"Alice","email":"alice@a.com","password":"ValidPass1!"}""")
        .exchange().expectStatus().isCreated();

    // Login as Alice (tenant A)
    String aliceToken = restTestClient.post().uri("/api/v1/auth/login")
        .header("X-Tenant-Code", "tenant-a")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("""{"email":"alice@a.com","password":"ValidPass1!"}""")
        .exchange().expectStatus().isOk()
        .expectBody(String.class).returnResult().getResponseBody();
    // (extract token from R wrapper)

    // Alice can see her own profile
    restTestClient.get().uri("/api/v1/users/{id}", aliceId)
        .header("Authorization", "Bearer " + aliceToken)
        .exchange().expectStatus().isOk();

    // Tenant B sees 0 users (RLS isolation)
    // Register a user in tenant B and log in as them
    // Verify they cannot see tenant A users (404 on Alice's ID)
    // ...implement assertion that confirms cross-tenant access returns 404
}
```
(The exact implementation depends on `KilnIntegrationTest`'s existing helpers — adapt accordingly.)

- [ ] Run: `./gradlew :app:test` — all green including new isolation test.

- [ ] Run: `./gradlew check` — all modules, all green.

- [ ] Run code review agent:
  ```
  Invoke superpowers:requesting-code-review
  ```
  Fix all issues found.

- [ ] Final commit:
```bash
git add .
git commit -m "✨ implement tenant module with PostgreSQL RLS, ScopedValue context, and admin CRUD API"
```

---

## Task 10 — Verification gate

- [ ] `./gradlew check` — all modules pass, zero test failures, JaCoCo thresholds met.
- [ ] `./gradlew :infra:generateJooq` — clean regeneration with tenant tables present.
- [ ] `./gradlew :app:bootRun` — app starts without errors.
- [ ] Add new CLAUDE.md trap if any new Spring Boot 4 / jOOQ / RLS issue was encountered.
- [ ] Update OpenAPI snapshot: `./gradlew :app:updateOpenApiSnapshot` then commit.
