# Dict Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use `- [ ]` syntax for tracking.

**Goal:** Implement a two-level data dictionary module with system-level dicts (shared, read-only) and tenant-level dicts (per-tenant custom values), backed by Redis caching for fast item lookup.

**Architecture:** Generic subdomain — simplified layout (`api/domain/repo/internal/config`). Two Flyway tables: `dict_types` (no RLS) and `dict_items` (RLS: system rows visible to all, tenant rows scoped). Redis caching via `RedisTemplate` with tenant-aware keys (`dict:{tenantId}:{typeCode}` → `List<DictItem>`), TTL=1h. Spring `@Cacheable` is NOT used — `TenantContext.CURRENT` is a Java 25 `ScopedValue` and cannot be read from a SpEL expression safely; explicit `RedisTemplate` calls give full control.

**Tech Stack:** Java 25, Spring Boot 4, jOOQ 3.20, Flyway, Redis (`RedisTemplate<String, Object>`), Testcontainers.

**Prerequisites:** Run `./gradlew check` — all 578 tests must be green before starting.

---

## File Map

### New module `dict/`
| File | Role |
|------|------|
| `dict/build.gradle` | Module deps |
| `dict/src/main/java/com/skyflux/kiln/dict/package-info.java` | `@ApplicationModule` |
| `dict/src/main/java/com/skyflux/kiln/dict/api/DictQueryService.java` | Public API — other modules call this |
| `dict/src/main/java/com/skyflux/kiln/dict/domain/DictType.java` | Domain record |
| `dict/src/main/java/com/skyflux/kiln/dict/domain/DictItem.java` | Domain record |
| `dict/src/main/java/com/skyflux/kiln/dict/repo/DictTypeJooqRepository.java` | jOOQ CRUD for dict_types |
| `dict/src/main/java/com/skyflux/kiln/dict/repo/DictItemJooqRepository.java` | jOOQ CRUD for dict_items |
| `dict/src/main/java/com/skyflux/kiln/dict/internal/DictService.java` | CRUD + cache eviction |
| `dict/src/main/java/com/skyflux/kiln/dict/internal/DictQueryServiceImpl.java` | Cached item lookup |
| `dict/src/main/java/com/skyflux/kiln/dict/internal/DictTypeController.java` | REST: types |
| `dict/src/main/java/com/skyflux/kiln/dict/internal/DictItemController.java` | REST: items |
| `dict/src/main/java/com/skyflux/kiln/dict/config/DictModuleConfig.java` | `@Configuration` bean wiring |

### New migrations
| File | Role |
|------|------|
| `infra/src/main/resources/db/migration/V15__dict.sql` | dict_types + dict_items + seed system dicts |
| `infra/src/main/resources/db/migration/R__rls.sql` | Append dict_items RLS policy |

### Modified files
| File | Change |
|------|--------|
| `settings.gradle` | Add `include 'dict'` |
| `app/build.gradle` | Add `implementation project(':dict')` |

---

## Task 1 — V15 migration + RLS + jOOQ regen

**Files:**
- Create: `infra/src/main/resources/db/migration/V15__dict.sql`
- Modify: `infra/src/main/resources/db/migration/R__rls.sql`

- [ ] Create `V15__dict.sql`:

```sql
-- V15__dict.sql
-- Two-level data dictionary: system dicts (tenant_id IS NULL) shared by all;
-- tenant dicts (tenant_id NOT NULL) scoped to the owning tenant.

CREATE TABLE dict_types (
    id         UUID         PRIMARY KEY,
    code       VARCHAR(50)  NOT NULL UNIQUE,
    name       VARCHAR(100) NOT NULL,
    is_system  BOOLEAN      NOT NULL DEFAULT false,
    tenant_id  UUID         REFERENCES tenants(id),  -- null = system type
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE  dict_types           IS 'Data dictionary type catalogue. No RLS — readable by all.';
COMMENT ON COLUMN dict_types.is_system IS 'true = seeded by migration, read-only via API.';
COMMENT ON COLUMN dict_types.tenant_id IS 'null for system types; tenant UUID for tenant-custom types.';

CREATE TABLE dict_items (
    id         UUID         PRIMARY KEY,
    type_id    UUID         NOT NULL REFERENCES dict_types(id) ON DELETE CASCADE,
    code       VARCHAR(50)  NOT NULL,
    label      VARCHAR(200) NOT NULL,
    sort_order INTEGER      NOT NULL DEFAULT 0,
    is_active  BOOLEAN      NOT NULL DEFAULT true,
    tenant_id  UUID         REFERENCES tenants(id),  -- mirrors dict_types.tenant_id
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT dict_items_type_code_unique UNIQUE (type_id, code)
);
COMMENT ON TABLE  dict_items           IS 'Dict items. RLS: system rows (tenant_id IS NULL) visible to all.';
COMMENT ON COLUMN dict_items.sort_order IS 'Ascending sort order for dropdown display.';

CREATE INDEX idx_dict_items_type_id  ON dict_items(type_id);
CREATE INDEX idx_dict_items_tenant   ON dict_items(tenant_id);

-- ── Seed: system-level dict types ──────────────────────────────────────────
INSERT INTO dict_types (id, code, name, is_system, tenant_id)
VALUES
    ('20000000-0000-7000-8000-000000000001', 'GENDER',        '性别', true, null),
    ('20000000-0000-7000-8000-000000000002', 'YES_NO',        '是否', true, null),
    ('20000000-0000-7000-8000-000000000003', 'ACTIVE_STATUS', '状态', true, null)
ON CONFLICT DO NOTHING;

INSERT INTO dict_items (id, type_id, code, label, sort_order, is_active, tenant_id)
VALUES
    -- GENDER
    ('21000000-0000-7000-8000-000000000001', '20000000-0000-7000-8000-000000000001', 'MALE',     '男',   1, true, null),
    ('21000000-0000-7000-8000-000000000002', '20000000-0000-7000-8000-000000000001', 'FEMALE',   '女',   2, true, null),
    ('21000000-0000-7000-8000-000000000003', '20000000-0000-7000-8000-000000000001', 'OTHER',    '其他', 3, true, null),
    -- YES_NO
    ('21000000-0000-7000-8000-000000000011', '20000000-0000-7000-8000-000000000002', 'YES',      '是',   1, true, null),
    ('21000000-0000-7000-8000-000000000012', '20000000-0000-7000-8000-000000000002', 'NO',       '否',   2, true, null),
    -- ACTIVE_STATUS
    ('21000000-0000-7000-8000-000000000021', '20000000-0000-7000-8000-000000000003', 'ACTIVE',   '启用', 1, true, null),
    ('21000000-0000-7000-8000-000000000022', '20000000-0000-7000-8000-000000000003', 'INACTIVE', '禁用', 2, true, null)
ON CONFLICT DO NOTHING;
```

- [ ] Append to `R__rls.sql` (add AFTER the last existing policy block):

```sql
-- dict_items: system rows (tenant_id IS NULL) visible to all authenticated users;
-- tenant rows visible only to their owning tenant.
ALTER TABLE dict_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE dict_items FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS dict_isolation ON dict_items;
CREATE POLICY dict_isolation ON dict_items
    USING (tenant_id IS NULL
           OR tenant_id = current_setting('app.tenant_id', true)::uuid);
```

- [ ] Regenerate jOOQ:
```bash
./gradlew :infra:generateJooq 2>&1 | grep -E "WARNING|ERROR|BUILD"
```
Expected: BUILD SUCCESSFUL, no warnings. Verify `Tables.DICT_TYPES` and `Tables.DICT_ITEMS` exist:
```bash
grep -r "DICT_TYPES\|DICT_ITEMS" infra/build/generated-src/jooq/main/ | head -3
```

- [ ] Commit:
```bash
git add infra/
git commit -m "✨ add dict_types and dict_items tables with system seed data (V15)"
```

---

## Task 2 — Scaffold dict Gradle module

**Files:**
- Modify: `settings.gradle`
- Create: `dict/build.gradle`
- Modify: `app/build.gradle`
- Create: `dict/src/main/java/com/skyflux/kiln/dict/package-info.java`

- [ ] Add to `settings.gradle` after the last `include`:
```groovy
include 'dict'
```

- [ ] Create `dict/build.gradle`:
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
    testImplementation 'com.redis:testcontainers-redis:2.2.4'
}

jacocoTestCoverageVerification {
    violationRules {
        rule {
            element = 'BUNDLE'
            limit { counter = 'LINE'; minimum = 0.70 }
            limit { counter = 'BRANCH'; minimum = 0.55 }
        }
    }
}
check.dependsOn jacocoTestCoverageVerification
```

- [ ] Add to `app/build.gradle` dependencies:
```groovy
implementation project(':dict')
```

- [ ] Create `dict/src/main/java/com/skyflux/kiln/dict/package-info.java`:
```java
@org.springframework.modulith.ApplicationModule(
    displayName = "Dict",
    allowedDependencies = {"common", "infra", "tenant"}
)
package com.skyflux.kiln.dict;
```

- [ ] Verify compilation: `./gradlew :dict:compileJava` — BUILD SUCCESSFUL (empty module).

- [ ] Commit:
```bash
git add settings.gradle dict/ app/build.gradle
git commit -m "✨ scaffold dict Gradle module"
```

---

## Task 3 — DictType + DictItem domain records (TDD)

**Files:**
- Create: `dict/src/main/java/com/skyflux/kiln/dict/domain/DictType.java`
- Create: `dict/src/main/java/com/skyflux/kiln/dict/domain/DictItem.java`
- Create: `dict/src/test/java/com/skyflux/kiln/dict/domain/DictTypeTest.java`
- Create: `dict/src/test/java/com/skyflux/kiln/dict/domain/DictItemTest.java`

- [ ] Write failing `DictTypeTest.java`:
```java
package com.skyflux.kiln.dict.domain;

import com.skyflux.kiln.common.util.Ids;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class DictTypeTest {
    @Test void shouldCreateSystemType() {
        DictType t = new DictType(Ids.next(), "GENDER", "性别", true, null, Instant.now());
        assertThat(t.isSystem()).isTrue();
        assertThat(t.tenantId()).isNull();
    }
    @Test void shouldCreateTenantType() {
        var tenantId = Ids.next();
        DictType t = new DictType(Ids.next(), "MY_CATEGORY", "自定义", false, tenantId, Instant.now());
        assertThat(t.isSystem()).isFalse();
        assertThat(t.tenantId()).isEqualTo(tenantId);
    }
}
```

- [ ] Write failing `DictItemTest.java`:
```java
package com.skyflux.kiln.dict.domain;

import com.skyflux.kiln.common.util.Ids;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class DictItemTest {
    @Test void shouldExposeFields() {
        var id = Ids.next(); var typeId = Ids.next();
        DictItem item = new DictItem(id, typeId, "MALE", "男", 1, true, null, Instant.now());
        assertThat(item.code()).isEqualTo("MALE");
        assertThat(item.label()).isEqualTo("男");
        assertThat(item.sortOrder()).isEqualTo(1);
        assertThat(item.isActive()).isTrue();
        assertThat(item.tenantId()).isNull();
    }
}
```

- [ ] Run: `./gradlew :dict:test` — FAIL (classes don't exist).

- [ ] Create `DictType.java`:
```java
package com.skyflux.kiln.dict.domain;

import java.time.Instant;
import java.util.UUID;

public record DictType(
        UUID id,
        String code,
        String name,
        boolean isSystem,
        UUID tenantId,   // null = system type
        Instant createdAt
) {}
```

- [ ] Create `DictItem.java`:
```java
package com.skyflux.kiln.dict.domain;

import java.time.Instant;
import java.util.UUID;

public record DictItem(
        UUID id,
        UUID typeId,
        String code,
        String label,
        int sortOrder,
        boolean isActive,
        UUID tenantId,   // null = system item
        Instant createdAt
) {}
```

- [ ] Run: `./gradlew :dict:test` — all GREEN.

- [ ] Commit:
```bash
git add dict/src/
git commit -m "✅ add DictType and DictItem domain records"
```

---

## Task 4 — Repositories (TDD, Testcontainers)

**Files:**
- Create: `dict/src/main/java/com/skyflux/kiln/dict/repo/DictTypeJooqRepository.java`
- Create: `dict/src/main/java/com/skyflux/kiln/dict/repo/DictItemJooqRepository.java`
- Create: `dict/src/test/java/com/skyflux/kiln/dict/repo/DictRepositoryTest.java`

- [ ] Write failing `DictRepositoryTest.java` (Testcontainers + `@SpringBootTest` — follow the pattern from `TenantJooqRepositoryTest`):

```java
package com.skyflux.kiln.dict.repo;

import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.dict.domain.DictItem;
import com.skyflux.kiln.dict.domain.DictType;
import com.skyflux.kiln.tenant.api.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = com.skyflux.kiln.dict.config.DictTestApp.class)
@Testcontainers
class DictRepositoryTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:18.3");

    @Autowired DictTypeJooqRepository typeRepo;
    @Autowired DictItemJooqRepository itemRepo;

    @Test void systemTypesAreSeededByV15() {
        // system types have tenant_id IS NULL and are visible without RLS context
        List<DictType> types = typeRepo.findAll();
        assertThat(types).extracting(DictType::code)
            .contains("GENDER", "YES_NO", "ACTIVE_STATUS");
    }

    @Test void systemItemsVisibleToAnyTenant() {
        UUID tenantId = UUID.fromString("00000000-0000-7000-8000-000000000001");
        List<DictItem> items = ScopedValue.where(TenantContext.CURRENT, tenantId)
            .call(() -> itemRepo.findActiveByTypeCode("GENDER"));
        assertThat(items).extracting(DictItem::code).contains("MALE", "FEMALE", "OTHER");
    }

    @Test void tenantItemsOnlyVisibleToOwningTenant() {
        UUID tenantA = UUID.fromString("00000000-0000-7000-8000-000000000001");
        UUID tenantB = Ids.next();

        // Create a tenant type + item for tenantA
        DictType type = new DictType(Ids.next(), "CUSTOM_A", "自定义A", false, tenantA, null);
        typeRepo.save(type);
        DictItem item = new DictItem(Ids.next(), type.id(), "VAL1", "值1", 1, true, tenantA, null);
        ScopedValue.where(TenantContext.CURRENT, tenantA).run(() -> itemRepo.save(item));

        // TenantA sees it
        List<DictItem> forA = ScopedValue.where(TenantContext.CURRENT, tenantA)
            .call(() -> itemRepo.findActiveByTypeCode("CUSTOM_A"));
        assertThat(forA).hasSize(1);

        // TenantB doesn't see it
        List<DictItem> forB = ScopedValue.where(TenantContext.CURRENT, tenantB)
            .call(() -> itemRepo.findActiveByTypeCode("CUSTOM_A"));
        assertThat(forB).isEmpty();
    }
}
```

Create `dict/src/test/java/com/skyflux/kiln/dict/config/DictTestApp.java` (follows the existing pattern in other modules — look at how `auth` or `member` module defines their test app):
```java
package com.skyflux.kiln.dict.config;

import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.skyflux.kiln")
public class DictTestApp {}
```

- [ ] Run: `./gradlew :dict:test --tests '*.DictRepositoryTest'` — FAIL (classes not found).

- [ ] Create `DictTypeJooqRepository.java`:
```java
package com.skyflux.kiln.dict.repo;

import com.skyflux.kiln.dict.domain.DictType;
import com.skyflux.kiln.infra.jooq.generated.Tables;
import com.skyflux.kiln.infra.jooq.generated.tables.records.DictTypesRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class DictTypeJooqRepository {

    private final DSLContext dsl;
    DictTypeJooqRepository(DSLContext dsl) { this.dsl = dsl; }

    public List<DictType> findAll() {
        return dsl.selectFrom(Tables.DICT_TYPES)
            .orderBy(Tables.DICT_TYPES.CODE)
            .fetch()
            .map(this::toType);
    }

    public Optional<DictType> findByCode(String code) {
        Objects.requireNonNull(code, "code");
        return dsl.selectFrom(Tables.DICT_TYPES)
            .where(Tables.DICT_TYPES.CODE.eq(code))
            .fetchOptional()
            .map(this::toType);
    }

    public void save(DictType type) {
        Objects.requireNonNull(type, "type");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        dsl.insertInto(Tables.DICT_TYPES)
            .set(Tables.DICT_TYPES.ID, type.id())
            .set(Tables.DICT_TYPES.CODE, type.code())
            .set(Tables.DICT_TYPES.NAME, type.name())
            .set(Tables.DICT_TYPES.IS_SYSTEM, type.isSystem())
            .set(Tables.DICT_TYPES.TENANT_ID, type.tenantId())
            .set(Tables.DICT_TYPES.CREATED_AT, now)
            .onConflict(Tables.DICT_TYPES.ID)
            .doUpdate()
            .set(Tables.DICT_TYPES.NAME, type.name())
            .execute();
    }

    public void delete(java.util.UUID id) {
        dsl.deleteFrom(Tables.DICT_TYPES).where(Tables.DICT_TYPES.ID.eq(id)).execute();
    }

    private DictType toType(DictTypesRecord r) {
        return new DictType(r.getId(), r.getCode(), r.getName(),
            Boolean.TRUE.equals(r.getIsSystem()), r.getTenantId(),
            r.getCreatedAt().toInstant());
    }
}
```

- [ ] Create `DictItemJooqRepository.java`:
```java
package com.skyflux.kiln.dict.repo;

import com.skyflux.kiln.dict.domain.DictItem;
import com.skyflux.kiln.infra.jooq.generated.Tables;
import com.skyflux.kiln.infra.jooq.generated.tables.records.DictItemsRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DictItemJooqRepository {

    private final DSLContext dsl;
    DictItemJooqRepository(DSLContext dsl) { this.dsl = dsl; }

    /** Returns active items for a type code, ordered by sort_order. RLS auto-filters. */
    public List<DictItem> findActiveByTypeCode(String typeCode) {
        Objects.requireNonNull(typeCode, "typeCode");
        return dsl.select(Tables.DICT_ITEMS.fields())
            .from(Tables.DICT_ITEMS)
            .join(Tables.DICT_TYPES).on(Tables.DICT_TYPES.ID.eq(Tables.DICT_ITEMS.TYPE_ID))
            .where(Tables.DICT_TYPES.CODE.eq(typeCode))
            .and(Tables.DICT_ITEMS.IS_ACTIVE.isTrue())
            .orderBy(Tables.DICT_ITEMS.SORT_ORDER, Tables.DICT_ITEMS.CODE)
            .fetch()
            .map(r -> toItem(r.into(Tables.DICT_ITEMS)));
    }

    public Optional<DictItem> findById(UUID id) {
        Objects.requireNonNull(id, "id");
        return dsl.selectFrom(Tables.DICT_ITEMS)
            .where(Tables.DICT_ITEMS.ID.eq(id))
            .fetchOptional()
            .map(this::toItem);
    }

    public void save(DictItem item) {
        Objects.requireNonNull(item, "item");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        dsl.insertInto(Tables.DICT_ITEMS)
            .set(Tables.DICT_ITEMS.ID, item.id())
            .set(Tables.DICT_ITEMS.TYPE_ID, item.typeId())
            .set(Tables.DICT_ITEMS.CODE, item.code())
            .set(Tables.DICT_ITEMS.LABEL, item.label())
            .set(Tables.DICT_ITEMS.SORT_ORDER, item.sortOrder())
            .set(Tables.DICT_ITEMS.IS_ACTIVE, item.isActive())
            .set(Tables.DICT_ITEMS.TENANT_ID, item.tenantId())
            .set(Tables.DICT_ITEMS.CREATED_AT, now)
            .onConflict(Tables.DICT_ITEMS.ID)
            .doUpdate()
            .set(Tables.DICT_ITEMS.LABEL, item.label())
            .set(Tables.DICT_ITEMS.SORT_ORDER, item.sortOrder())
            .set(Tables.DICT_ITEMS.IS_ACTIVE, item.isActive())
            .execute();
    }

    public void delete(UUID id) {
        dsl.deleteFrom(Tables.DICT_ITEMS).where(Tables.DICT_ITEMS.ID.eq(id)).execute();
    }

    private DictItem toItem(DictItemsRecord r) {
        return new DictItem(r.getId(), r.getTypeId(), r.getCode(), r.getLabel(),
            r.getSortOrder() == null ? 0 : r.getSortOrder(),
            Boolean.TRUE.equals(r.getIsActive()), r.getTenantId(),
            r.getCreatedAt().toInstant());
    }
}
```

- [ ] Run: `./gradlew :dict:test --tests '*.DictRepositoryTest'` — all 3 tests PASS.

- [ ] Commit:
```bash
git add dict/src/
git commit -m "✅ add DictTypeJooqRepository and DictItemJooqRepository with RLS isolation test"
```

---

## Task 5 — DictQueryService with Redis caching (TDD)

**Files:**
- Create: `dict/src/main/java/com/skyflux/kiln/dict/api/DictQueryService.java`
- Create: `dict/src/main/java/com/skyflux/kiln/dict/internal/DictQueryServiceImpl.java`
- Create: `dict/src/main/java/com/skyflux/kiln/dict/config/DictModuleConfig.java`
- Create: `dict/src/test/java/com/skyflux/kiln/dict/internal/DictQueryServiceImplTest.java`

- [ ] Create `DictQueryService.java` (public API):
```java
package com.skyflux.kiln.dict.api;

import com.skyflux.kiln.dict.domain.DictItem;
import java.util.List;

/**
 * Public API for dictionary item lookup.
 * Results are Redis-cached per tenant+typeCode for 1 hour.
 * Other modules may depend on this interface.
 */
public interface DictQueryService {
    /** Returns active items for the given type code, ordered by sort_order. */
    List<DictItem> getItems(String typeCode);
}
```

- [ ] Write failing `DictQueryServiceImplTest.java`:
```java
package com.skyflux.kiln.dict.internal;

import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.dict.domain.DictItem;
import com.skyflux.kiln.dict.repo.DictItemJooqRepository;
import com.skyflux.kiln.tenant.api.TenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DictQueryServiceImplTest {

    @Mock DictItemJooqRepository itemRepo;
    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ValueOperations<String, Object> valueOps;
    @InjectMocks DictQueryServiceImpl service;

    private static final UUID TENANT = Ids.next();
    private static final DictItem ITEM = new DictItem(
        Ids.next(), Ids.next(), "MALE", "男", 1, true, null, Instant.now());

    @Test void shouldReturnCachedItemsWhenCacheHits() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("dict:" + TENANT + ":GENDER")).thenReturn(List.of(ITEM));

        List<DictItem> result = ScopedValue.where(TenantContext.CURRENT, TENANT)
            .call(() -> service.getItems("GENDER"));

        assertThat(result).containsExactly(ITEM);
        verifyNoInteractions(itemRepo);
    }

    @Test void shouldQueryDbAndCacheOnMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("dict:" + TENANT + ":GENDER")).thenReturn(null);
        when(itemRepo.findActiveByTypeCode("GENDER")).thenReturn(List.of(ITEM));

        List<DictItem> result = ScopedValue.where(TenantContext.CURRENT, TENANT)
            .call(() -> service.getItems("GENDER"));

        assertThat(result).containsExactly(ITEM);
        verify(valueOps).set(eq("dict:" + TENANT + ":GENDER"), eq(List.of(ITEM)), any());
    }

    @Test void shouldUseNullKeyForUnauthenticatedContext() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("dict:null:GENDER")).thenReturn(List.of(ITEM));

        // No TenantContext bound — key uses "null" as tenantId segment
        List<DictItem> result = service.getItems("GENDER");
        assertThat(result).containsExactly(ITEM);
    }
}
```

- [ ] Run: `./gradlew :dict:test --tests '*.DictQueryServiceImplTest'` — FAIL.

- [ ] Create `DictQueryServiceImpl.java`:
```java
package com.skyflux.kiln.dict.internal;

import com.skyflux.kiln.dict.api.DictQueryService;
import com.skyflux.kiln.dict.domain.DictItem;
import com.skyflux.kiln.dict.repo.DictItemJooqRepository;
import com.skyflux.kiln.tenant.api.TenantContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
class DictQueryServiceImpl implements DictQueryService {

    private static final Duration TTL = Duration.ofHours(1);

    private final DictItemJooqRepository itemRepo;
    private final RedisTemplate<String, Object> redisTemplate;

    DictQueryServiceImpl(DictItemJooqRepository itemRepo,
                         RedisTemplate<String, Object> redisTemplate) {
        this.itemRepo = itemRepo;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<DictItem> getItems(String typeCode) {
        String key = cacheKey(typeCode);
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof List<?> list) {
            return (List<DictItem>) list;
        }
        List<DictItem> items = itemRepo.findActiveByTypeCode(typeCode);
        redisTemplate.opsForValue().set(key, items, TTL);
        return items;
    }

    void evict(String typeCode) {
        redisTemplate.delete(cacheKey(typeCode));
    }

    private static String cacheKey(String typeCode) {
        UUID tenantId = TenantContext.CURRENT.isBound() ? TenantContext.CURRENT.get() : null;
        return "dict:" + tenantId + ":" + typeCode;
    }
}
```

- [ ] Create `DictModuleConfig.java`:
```java
package com.skyflux.kiln.dict.config;

import org.springframework.context.annotation.Configuration;

/** Module-level Spring configuration placeholder. Beans are auto-detected via component scan. */
@Configuration
class DictModuleConfig {}
```

- [ ] Run: `./gradlew :dict:test --tests '*.DictQueryServiceImplTest'` — all 3 tests PASS.

- [ ] Run: `./gradlew :dict:test` — all GREEN.

- [ ] Commit:
```bash
git add dict/src/
git commit -m "✅ add DictQueryService with Redis cache (hit/miss/evict) and tenant-aware key"
```

---

## Task 6 — DictService: CRUD with cache eviction (TDD)

**Files:**
- Create: `dict/src/main/java/com/skyflux/kiln/dict/internal/DictService.java`
- Create: `dict/src/test/java/com/skyflux/kiln/dict/internal/DictServiceTest.java`

- [ ] Write failing `DictServiceTest.java`:
```java
package com.skyflux.kiln.dict.internal;

import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.dict.domain.DictItem;
import com.skyflux.kiln.dict.domain.DictType;
import com.skyflux.kiln.dict.repo.DictItemJooqRepository;
import com.skyflux.kiln.dict.repo.DictTypeJooqRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DictServiceTest {

    @Mock DictTypeJooqRepository typeRepo;
    @Mock DictItemJooqRepository itemRepo;
    @Mock DictQueryServiceImpl cache;
    @InjectMocks DictService service;

    @Test void shouldCreateTenantType() {
        when(typeRepo.findByCode("MY_TYPE")).thenReturn(Optional.empty());
        UUID tenantId = Ids.next();
        DictType result = service.createType("MY_TYPE", "我的类型", tenantId);
        assertThat(result.code()).isEqualTo("MY_TYPE");
        assertThat(result.isSystem()).isFalse();
        assertThat(result.tenantId()).isEqualTo(tenantId);
        verify(typeRepo).save(any());
    }

    @Test void shouldRejectDuplicateTypeCode() {
        when(typeRepo.findByCode("GENDER")).thenReturn(Optional.of(
            new DictType(Ids.next(), "GENDER", "性别", true, null, Instant.now())));
        assertThatThrownBy(() -> service.createType("GENDER", "copy", Ids.next()))
            .isInstanceOf(AppException.class)
            .extracting(e -> ((AppException) e).appCode())
            .isEqualTo(AppCode.CONFLICT);
    }

    @Test void shouldAddItemAndEvictCache() {
        DictType type = new DictType(Ids.next(), "GENDER", "性别", false, Ids.next(), Instant.now());
        when(typeRepo.findByCode("GENDER")).thenReturn(Optional.of(type));
        service.addItem("GENDER", "OTHER2", "其他2", 99);
        verify(itemRepo).save(any());
        verify(cache).evict("GENDER");
    }

    @Test void shouldUpdateItemAndEvictCache() {
        UUID id = Ids.next(); UUID typeId = Ids.next();
        DictItem item = new DictItem(id, typeId, "MALE", "男", 1, true, null, Instant.now());
        when(itemRepo.findById(id)).thenReturn(Optional.of(item));
        when(typeRepo.findByCode(any())).thenReturn(Optional.empty()); // type code needed for evict
        // Look up type code from typeId — accept that we query by typeId is not in the plan.
        // Simpler: evict is called with the item's typeCode — pass it explicitly.
        service.updateItem(id, "男性", 1, true, "GENDER");
        verify(itemRepo).save(any());
        verify(cache).evict("GENDER");
    }

    @Test void shouldDeleteItemAndEvictCache() {
        UUID id = Ids.next();
        DictItem item = new DictItem(id, Ids.next(), "MALE", "男", 1, true, null, Instant.now());
        when(itemRepo.findById(id)).thenReturn(Optional.of(item));
        service.deleteItem(id, "GENDER");
        verify(itemRepo).delete(id);
        verify(cache).evict("GENDER");
    }
}
```

- [ ] Run: `./gradlew :dict:test --tests '*.DictServiceTest'` — FAIL.

- [ ] Create `DictService.java`:
```java
package com.skyflux.kiln.dict.internal;

import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.dict.domain.DictItem;
import com.skyflux.kiln.dict.domain.DictType;
import com.skyflux.kiln.dict.repo.DictItemJooqRepository;
import com.skyflux.kiln.dict.repo.DictTypeJooqRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
class DictService {

    private final DictTypeJooqRepository typeRepo;
    private final DictItemJooqRepository itemRepo;
    private final DictQueryServiceImpl cache;

    DictService(DictTypeJooqRepository typeRepo, DictItemJooqRepository itemRepo,
                DictQueryServiceImpl cache) {
        this.typeRepo = typeRepo; this.itemRepo = itemRepo; this.cache = cache;
    }

    @Transactional
    public DictType createType(String code, String name, UUID tenantId) {
        if (typeRepo.findByCode(code).isPresent()) {
            throw new AppException(AppCode.CONFLICT, "Dict type code already exists: " + code);
        }
        DictType type = new DictType(Ids.next(), code, name, false, tenantId, null);
        typeRepo.save(type);
        return type;
    }

    public List<DictType> listTypes() { return typeRepo.findAll(); }

    @Transactional
    public DictItem addItem(String typeCode, String code, String label, int sortOrder) {
        DictType type = typeRepo.findByCode(typeCode)
            .orElseThrow(() -> new AppException(AppCode.NOT_FOUND, "Dict type not found: " + typeCode));
        DictItem item = new DictItem(Ids.next(), type.id(), code, label, sortOrder, true,
            type.tenantId(), null);
        itemRepo.save(item);
        cache.evict(typeCode);
        return item;
    }

    @Transactional
    public DictItem updateItem(UUID itemId, String label, int sortOrder, boolean isActive,
                               String typeCode) {
        DictItem existing = itemRepo.findById(itemId)
            .orElseThrow(() -> new AppException(AppCode.NOT_FOUND));
        DictItem updated = new DictItem(existing.id(), existing.typeId(), existing.code(),
            label, sortOrder, isActive, existing.tenantId(), existing.createdAt());
        itemRepo.save(updated);
        cache.evict(typeCode);
        return updated;
    }

    @Transactional
    public void deleteItem(UUID itemId, String typeCode) {
        itemRepo.findById(itemId).orElseThrow(() -> new AppException(AppCode.NOT_FOUND));
        itemRepo.delete(itemId);
        cache.evict(typeCode);
    }
}
```

- [ ] Run: `./gradlew :dict:test --tests '*.DictServiceTest'` — all PASS.

- [ ] Commit:
```bash
git add dict/src/
git commit -m "✅ add DictService with CRUD and cache eviction"
```

---

## Task 7 — REST controllers (TDD)

**Files:**
- Create: `dict/src/main/java/com/skyflux/kiln/dict/internal/DictTypeController.java`
- Create: `dict/src/main/java/com/skyflux/kiln/dict/internal/DictItemController.java`
- Create: `dict/src/test/java/com/skyflux/kiln/dict/internal/DictTypeControllerTest.java`
- Create: `dict/src/test/java/com/skyflux/kiln/dict/internal/DictItemControllerTest.java`

- [ ] Create `DictTypeController.java`:
```java
package com.skyflux.kiln.dict.internal;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.skyflux.kiln.common.result.R;
import com.skyflux.kiln.dict.domain.DictType;
import com.skyflux.kiln.tenant.api.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
class DictTypeController {

    private final DictService service;
    DictTypeController(DictService service) { this.service = service; }

    @GetMapping("/api/v1/dict/types")
    @SaCheckLogin
    R<List<DictTypeResponse>> list() {
        return R.ok(service.listTypes().stream().map(DictTypeResponse::from).toList());
    }

    @PostMapping("/api/v1/admin/dict/types")
    @SaCheckRole("ADMIN")
    @ResponseStatus(HttpStatus.CREATED)
    R<DictTypeResponse> create(@Valid @RequestBody CreateDictTypeRequest req) {
        UUID tenantId = TenantContext.CURRENT.get();
        return R.ok(DictTypeResponse.from(service.createType(req.code(), req.name(), tenantId)));
    }

    record CreateDictTypeRequest(
        @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Z][A-Z0-9_]*") String code,
        @NotBlank @Size(max = 100) String name) {}

    record DictTypeResponse(String id, String code, String name, boolean isSystem) {
        static DictTypeResponse from(DictType t) {
            return new DictTypeResponse(t.id().toString(), t.code(), t.name(), t.isSystem());
        }
    }
}
```

- [ ] Create `DictItemController.java`:
```java
package com.skyflux.kiln.dict.internal;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.skyflux.kiln.common.result.R;
import com.skyflux.kiln.dict.api.DictQueryService;
import com.skyflux.kiln.dict.domain.DictItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
class DictItemController {

    private final DictQueryService queryService;
    private final DictService service;

    DictItemController(DictQueryService queryService, DictService service) {
        this.queryService = queryService; this.service = service;
    }

    @GetMapping("/api/v1/dict/{typeCode}/items")
    @SaCheckLogin
    R<List<DictItemResponse>> items(@PathVariable String typeCode) {
        return R.ok(queryService.getItems(typeCode).stream().map(DictItemResponse::from).toList());
    }

    @PostMapping("/api/v1/admin/dict/types/{typeCode}/items")
    @SaCheckRole("ADMIN")
    @ResponseStatus(HttpStatus.CREATED)
    R<DictItemResponse> addItem(@PathVariable String typeCode,
                                @Valid @RequestBody AddDictItemRequest req) {
        return R.ok(DictItemResponse.from(
            service.addItem(typeCode, req.code(), req.label(), req.sortOrder())));
    }

    @PutMapping("/api/v1/admin/dict/items/{itemId}")
    @SaCheckRole("ADMIN")
    R<DictItemResponse> updateItem(@PathVariable UUID itemId,
                                   @Valid @RequestBody UpdateDictItemRequest req) {
        return R.ok(DictItemResponse.from(
            service.updateItem(itemId, req.label(), req.sortOrder(), req.isActive(), req.typeCode())));
    }

    @DeleteMapping("/api/v1/admin/dict/items/{itemId}")
    @SaCheckRole("ADMIN")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteItem(@PathVariable UUID itemId, @RequestParam String typeCode) {
        service.deleteItem(itemId, typeCode);
    }

    record AddDictItemRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 200) String label,
        int sortOrder) {}

    record UpdateDictItemRequest(
        @NotBlank @Size(max = 200) String label,
        int sortOrder,
        boolean isActive,
        @NotBlank String typeCode) {}

    record DictItemResponse(String id, String code, String label, int sortOrder, boolean isActive) {
        static DictItemResponse from(DictItem i) {
            return new DictItemResponse(i.id().toString(), i.code(), i.label(),
                i.sortOrder(), i.isActive());
        }
    }
}
```

- [ ] Write `DictTypeControllerTest.java` using `@WebMvcTest` pattern (follow `TenantControllerTest` for exact import and MockBean pattern). Mock `DictService`. Test: GET list returns 200 with types in `$.data`; POST create returns 201.

- [ ] Write `DictItemControllerTest.java`. Mock `DictQueryService` and `DictService`. Test: GET items returns 200 with items in `$.data`; POST addItem returns 201; PUT updateItem returns 200; DELETE returns 204.

- [ ] Run: `./gradlew :dict:test` — all GREEN.

- [ ] Commit:
```bash
git add dict/src/
git commit -m "✨ add DictTypeController and DictItemController REST endpoints"
```

---

## Task 8 — Full verification, code review, merge, push

- [ ] Run full check:
```bash
./gradlew check 2>&1 | tail -10
```
Must be BUILD SUCCESSFUL, 0 failures.

- [ ] Invoke `superpowers:requesting-code-review` skill. Fix all Medium+ findings.

- [ ] Update OpenAPI snapshot:
```bash
./gradlew :app:updateOpenApiSnapshot
git add docs/openapi-snapshot.json
git diff --cached --quiet || git commit -m "🔧 update OpenAPI snapshot for dict endpoints"
```

- [ ] Squash and merge to main (follow the same pattern as previous feature branches):
```bash
MERGE_BASE=$(git merge-base HEAD main)
git reset --soft $MERGE_BASE
git reset HEAD -- .
git add dict/ infra/ settings.gradle app/build.gradle docs/openapi-snapshot.json
git commit -m "✨ add Dict module: two-level dictionary with Redis caching and tenant isolation"
cd /Users/martinadamsdev/workspace/kiln
git checkout main
git merge --ff-only feature/dict
git push origin main
git worktree remove --force .worktrees/feature-dict
git branch -d feature/dict
```

- [ ] Apply V15 to local dev DB:
```bash
./gradlew :app:bootRun
# Ctrl-C after "Started KilnApplication"
```

- [ ] Report total test count and final commit SHA.
