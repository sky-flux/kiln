# Dict Module Design

**Goal:** Two-level data dictionary — system-level dicts (read-only, shared across all tenants) + tenant-level dicts (each tenant customises their own values). Redis cache for fast item lookup.

**Architecture:** Generic subdomain, simplified layout (`api/domain/repo/internal/config`). Two tables: `dict_types` (type catalogue, no RLS) and `dict_items` (items, RLS allows system rows + tenant rows). Redis caching via Spring Cache `@Cacheable`/`@CacheEvict` with TTL=1h.

---

## Data Model

### `dict_types`
```sql
CREATE TABLE dict_types (
    id         UUID PRIMARY KEY,
    code       VARCHAR(50)  NOT NULL UNIQUE,
    name       VARCHAR(100) NOT NULL,
    is_system  BOOLEAN      NOT NULL DEFAULT false,
    tenant_id  UUID REFERENCES tenants(id),  -- null = system type
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
```
No RLS — type catalogue is readable by all authenticated users.

### `dict_items`
```sql
CREATE TABLE dict_items (
    id         UUID PRIMARY KEY,
    type_id    UUID        NOT NULL REFERENCES dict_types(id) ON DELETE CASCADE,
    code       VARCHAR(50)  NOT NULL,
    label      VARCHAR(200) NOT NULL,
    sort_order INTEGER      NOT NULL DEFAULT 0,
    is_active  BOOLEAN      NOT NULL DEFAULT true,
    tenant_id  UUID REFERENCES tenants(id),  -- mirrors dict_types.tenant_id
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT dict_items_type_code_unique UNIQUE (type_id, code)
);
-- RLS: system items (tenant_id IS NULL) visible to everyone;
--      tenant items only visible to the owning tenant.
CREATE POLICY dict_isolation ON dict_items
    USING (tenant_id IS NULL
           OR tenant_id = current_setting('app.tenant_id', true)::uuid);
```

---

## API

| Endpoint | Auth | Description |
|----------|------|-------------|
| `GET /api/v1/dict/types` | `@SaCheckLogin` | List all visible types (system + own tenant) |
| `GET /api/v1/dict/{typeCode}/items` | `@SaCheckLogin` | Get active items for type — **Redis cached** |
| `POST /api/v1/admin/dict/types` | `@SaCheckRole("ADMIN")` | Create type (is_system=false, tenant_id=current) |
| `POST /api/v1/admin/dict/types/{typeCode}/items` | `@SaCheckRole("ADMIN")` | Add item to type |
| `PUT /api/v1/admin/dict/items/{itemId}` | `@SaCheckRole("ADMIN")` | Update item label/sort/active |
| `DELETE /api/v1/admin/dict/items/{itemId}` | `@SaCheckRole("ADMIN")` | Delete item |

---

## Redis Cache

- **Key**: `dict:{tenantId}:{typeCode}` → `List<DictItem>` (active items only, sorted by sort_order)
- **Strategy**: `@Cacheable` on read; `@CacheEvict` on create/update/delete
- **TTL**: 1 hour (configurable via `kiln.dict.cache-ttl-seconds`)
- **Scope**: system items + tenant items merged by the DB query (RLS handles filtering automatically)

---

## Module Layout (Generic — simplified)

```
dict/
├── api/
│   └── DictQueryService.java      ← public: getItems(typeCode) — used by other modules
├── domain/
│   ├── DictType.java              ← record: id, code, name, isSystem, tenantId
│   └── DictItem.java              ← record: id, typeId, code, label, sortOrder, isActive, tenantId
├── repo/
│   ├── DictTypeJooqRepository.java
│   └── DictItemJooqRepository.java
├── internal/
│   ├── DictService.java           ← CRUD + cache eviction
│   ├── DictTypeController.java    ← GET /dict/types, POST /admin/dict/types
│   └── DictItemController.java    ← GET /dict/{typeCode}/items, POST/PUT/DELETE /admin/dict/items
└── config/
    └── DictCacheConfig.java       ← RedisCacheManager bean + TTL
```

---

## Seed Data

V15 migration seeds system-level dict types:

| code | name | items |
|------|------|-------|
| `GENDER` | 性别 | MALE=男, FEMALE=女, OTHER=其他 |
| `YES_NO` | 是否 | YES=是, NO=否 |
| `ACTIVE_STATUS` | 状态 | ACTIVE=启用, INACTIVE=禁用 |

---

## Constraints

- Tenant admins can only create/modify their OWN tenant's dict items (not system items)
- System dict types (`is_system=true`) cannot be deleted via API — only seeded via migration
- Item codes are immutable after creation (other code may reference them by code string)
- `@CacheEvict` must use `allEntries=true` or key-specific eviction — key includes tenantId
