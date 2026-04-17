# Role CRUD — Wave 2b Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans`. Steps use `- [ ]` syntax.

**Goal:** Upgrade `auth` module from static `ADMIN/USER` enum roles to dynamic, tenant-scoped role management: full CRUD for roles and permissions, admin endpoints, and `RoleCode` enum replaced by database-driven lookup.

**Architecture:** Generic subdomain — `auth` module already uses simplified layout (`api/domain/repo/internal/config`). Roles become tenant-scoped: `roles.tenant_id` (new column). System roles (`ADMIN`, `USER`) belong to the system tenant (`00000000-0000-7000-8000-000000000001`). New tenant roles are created by that tenant's admin. `StpInterfaceImpl` already queries DB — no behavioural change needed, just schema change. `RoleCode` enum stays for system roles; string codes handle tenant-specific roles.

**Tech Stack:** Java 25, Spring Boot 4, jOOQ 3.20, existing `auth` module layout.

**Prerequisites:**
- Wave 1 complete (tenant module live, system tenant seeded).
- Wave 2a does NOT need to complete before Wave 2b — run these in parallel.
- `./gradlew check` all green before starting.

---

## File Map

### New migration
| File | Change |
|------|--------|
| `infra/src/main/resources/db/migration/V9__roles_tenant.sql` | Add `tenant_id` to `roles`; add RLS |
| `infra/src/main/resources/db/migration/R__rls.sql` | Append `roles` RLS policy |

### New production files
| File | Role |
|------|------|
| `auth/src/main/java/com/skyflux/kiln/auth/internal/RoleCrudService.java` | CRUD for roles + permissions |
| `auth/src/main/java/com/skyflux/kiln/auth/internal/RoleCrudController.java` | Admin REST for role/permission management |

### Modified files
| File | Change |
|------|--------|
| `auth/src/main/java/com/skyflux/kiln/auth/repo/RoleJooqRepository.java` | Add `save`, `delete`, `listByTenant` |
| `auth/src/main/java/com/skyflux/kiln/auth/repo/PermissionJooqRepository.java` | Add `save`, `delete`, `listByTenant` |
| `auth/src/main/java/com/skyflux/kiln/auth/domain/Role.java` | Add `tenantId UUID` field |
| `auth/src/main/java/com/skyflux/kiln/auth/internal/UserRegisteredListener.java` | Resolve ADMIN/USER from DB by system tenant |
| `infra/build.gradle` | Add V9 to jOOQ scripts list |

---

## Task 1 — Flyway migration: roles.tenant_id

- [ ] Create `V9__roles_tenant.sql`:
```sql
-- V9__roles_tenant.sql
-- Makes roles tenant-scoped. System roles (ADMIN, USER) belong to system tenant.

ALTER TABLE roles ADD COLUMN tenant_id UUID REFERENCES tenants(id);
UPDATE roles SET tenant_id = '00000000-0000-7000-8000-000000000001';
ALTER TABLE roles ALTER COLUMN tenant_id SET NOT NULL;

-- Role code is unique within a tenant, not globally
ALTER TABLE roles DROP CONSTRAINT IF EXISTS roles_code_key;
ALTER TABLE roles ADD CONSTRAINT roles_code_tenant_unique UNIQUE (code, tenant_id);

COMMENT ON COLUMN roles.tenant_id IS 'Owning tenant. System roles belong to system tenant.';
```

- [ ] Append to `R__rls.sql`:
```sql
-- roles: isolate by tenant_id
ALTER TABLE roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE roles FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON roles;
CREATE POLICY tenant_isolation ON roles
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
```

- [ ] Add `V9__roles_tenant.sql` to jOOQ scripts list in `infra/build.gradle`.

- [ ] Regenerate jOOQ: `./gradlew :infra:generateJooq`
  Expected: `RolesRecord` gains `getTenantId()`.

- [ ] Commit:
```bash
git add infra/
git commit -m "✨ add roles.tenant_id column and RLS policy for tenant-scoped roles"
```

---

## Task 2 — Update `Role` domain model (TDD)

- [ ] Write failing test (add to existing `auth` test or create `RoleTest`):
```java
@Test void roleShouldCarryTenantId() {
    UUID tenantId = Ids.next();
    UUID roleId = Ids.next();
    Role role = new Role(roleId, "MANAGER", "Manager", tenantId);
    assertThat(role.tenantId()).isEqualTo(tenantId);
}
```

- [ ] Update `Role.java`:
```java
package com.skyflux.kiln.auth.domain;

import java.util.UUID;

public record Role(UUID id, String code, String name, UUID tenantId) {}
```

- [ ] Update all `Role` construction call sites in `RoleJooqRepository`, `PermissionLookupServiceImpl`, etc. to pass `tenantId`.

- [ ] Update `RoleJooqRepository.findByCode()` and `findById()` to map `r.getTenantId()`.

- [ ] Run: `./gradlew :auth:test` — all green.

- [ ] Commit: `git commit -m "✅ add tenantId to Role domain record"`

---

## Task 3 — RoleJooqRepository CRUD extensions (TDD)

- [ ] Write failing tests in `RoleJooqRepositoryTest` (use Testcontainers — follow `TenantJooqRepositoryTest` pattern):
```java
@Test void shouldSaveNewRole() {
    UUID tenantId = /* system tenant id from V4 seed */ UUID.fromString("00000000-0000-7000-8000-000000000001");
    // Must set app.tenant_id for the test session
    dsl.execute("SET LOCAL app.tenant_id = '" + tenantId + "'");
    UUID id = Ids.next();
    repo.save(new Role(id, "EDITOR", "Editor", tenantId));
    assertThat(repo.findById(id)).isPresent();
}

@Test void shouldDeleteRole() {
    // seed + delete + verify empty
}

@Test void shouldListRolesByTenant() {
    // ADMIN + USER pre-seeded; list should include them
}
```

- [ ] Add methods to `RoleJooqRepository`:
```java
public void save(Role role) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    dsl.insertInto(Tables.ROLES)
        .set(Tables.ROLES.ID, role.id())
        .set(Tables.ROLES.CODE, role.code())
        .set(Tables.ROLES.NAME, role.name())
        .set(Tables.ROLES.TENANT_ID, role.tenantId())
        .set(Tables.ROLES.CREATED_AT, now)
        .onConflict(Tables.ROLES.ID)
        .doUpdate()
        .set(Tables.ROLES.NAME, role.name())
        .execute();
}

public void delete(UUID roleId) {
    dsl.deleteFrom(Tables.ROLES).where(Tables.ROLES.ID.eq(roleId)).execute();
}

public List<Role> listAll() {
    return dsl.selectFrom(Tables.ROLES)
        .orderBy(Tables.ROLES.CODE)
        .fetch()
        .map(r -> new Role(r.getId(), r.getCode(), r.getName(), r.getTenantId()));
}
```

- [ ] Run: `./gradlew :auth:test` — all green.

- [ ] Commit: `git commit -m "✅ extend RoleJooqRepository with save, delete, listAll"`

---

## Task 4 — RoleCrudService + RoleCrudController (TDD)

- [ ] Write failing `RoleCrudServiceTest`:
```java
@Test void shouldCreateRole() {
    UUID tenantId = Ids.next();
    // Mock TenantContext not needed — service takes tenantId as param
    when(roleRepo.findByCode("MANAGER")).thenReturn(Optional.empty());
    Role created = service.createRole(tenantId, "MANAGER", "Manager");
    assertThat(created.code()).isEqualTo("MANAGER");
    verify(roleRepo).save(any());
}

@Test void shouldRejectDuplicateRoleCode() {
    UUID tenantId = Ids.next();
    when(roleRepo.findByCode("MANAGER")).thenReturn(Optional.of(new Role(Ids.next(), "MANAGER", "X", tenantId)));
    assertThatThrownBy(() -> service.createRole(tenantId, "MANAGER", "Dup"))
        .hasMessageContaining("already exists");
}
```

- [ ] Implement `RoleCrudService`:
```java
@Service
class RoleCrudService {
    private final RoleJooqRepository roleRepo;
    RoleCrudService(RoleJooqRepository roleRepo) { this.roleRepo = roleRepo; }

    @Transactional
    public Role createRole(UUID tenantId, String code, String name) {
        if (roleRepo.findByCode(code).isPresent()) {
            throw new AppException(AppCode.CONFLICT, "Role code already exists: " + code);
        }
        Role role = new Role(Ids.next(), code, name, tenantId);
        roleRepo.save(role);
        return role;
    }

    @Transactional
    public void deleteRole(UUID roleId) { roleRepo.delete(roleId); }

    public List<Role> listRoles() { return roleRepo.listAll(); }
}
```

- [ ] Create `RoleCrudController.java` — admin-only endpoints:
```
POST   /api/v1/admin/roles          → createRole
GET    /api/v1/admin/roles          → listRoles
DELETE /api/v1/admin/roles/{id}     → deleteRole
```

All guarded by `@SaCheckRole("ADMIN")`.

```java
@RestController
@RequestMapping("/api/v1/admin/roles")
@SaCheckRole("ADMIN")
class RoleCrudController {
    private final RoleCrudService service;
    RoleCrudController(RoleCrudService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RoleResponse create(@Valid @RequestBody CreateRoleRequest req) {
        // TenantContext.CURRENT is bound by TenantFilter
        UUID tenantId = com.skyflux.kiln.tenant.api.TenantContext.CURRENT.get();
        return RoleResponse.from(service.createRole(tenantId, req.code(), req.name()));
    }

    @GetMapping
    List<RoleResponse> list() {
        return service.listRoles().stream().map(RoleResponse::from).toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) { service.deleteRole(id); }

    record CreateRoleRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 100) String name) {}

    record RoleResponse(String id, String code, String name) {
        static RoleResponse from(Role r) {
            return new RoleResponse(r.id().toString(), r.code(), r.name());
        }
    }
}
```

Note: `auth` module already depends on `user`; to read `TenantContext` from `tenant.api`, add `implementation project(':tenant')` to `auth/build.gradle`.

- [ ] Run: `./gradlew :auth:test` — all green.

- [ ] Run: `./gradlew check` — all modules green.

- [ ] Run code review: invoke `superpowers:requesting-code-review`. Fix all findings.

- [ ] Commit:
```bash
git add .
git commit -m "✨ add dynamic tenant-scoped Role CRUD with admin API"
```

- [ ] Update OpenAPI snapshot: `./gradlew :app:updateOpenApiSnapshot` then commit.
