# User CRUD Complement — Wave 2a Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans`. Steps use `- [ ]` syntax.

**Goal:** Complete User CRUD — add paginated list, update (display name), and soft-delete (status flag) — all tenant-scoped via the Wave 1 RLS infrastructure.

**Architecture:** Extends the existing hexagonal `user` module. Three new use cases (`ListUsersUseCase`, `UpdateUserUseCase`, `DeleteUserUseCase`) following the existing `GetUserUseCase` / `RegisterUserUseCase` pattern. Database layer: one new Flyway migration adds `status VARCHAR(20)` to `users` (soft-delete flag). `TenantContext.CURRENT` is already bound by `TenantFilter` — no additional context wiring needed. Paginated list uses `PageQuery` / `PageResult` from `common`.

**Tech Stack:** Java 25, Spring Boot 4, jOOQ 3.20, existing `user` hexagonal layout.

**Prerequisites:**
- Wave 1 complete (`tenant` module live, `users.tenant_id` + RLS in place).
- `./gradlew check` all green before starting.

---

## File Map

### New migration
| File | Change |
|------|--------|
| `infra/src/main/resources/db/migration/V8__users_status.sql` | Add `users.status VARCHAR(20) DEFAULT 'ACTIVE'` |

### New production files (user module)
| File | Role |
|------|------|
| `user/src/main/java/com/skyflux/kiln/user/application/port/in/ListUsersUseCase.java` | Inbound port |
| `user/src/main/java/com/skyflux/kiln/user/application/port/in/UpdateUserUseCase.java` | Inbound port |
| `user/src/main/java/com/skyflux/kiln/user/application/port/in/DeleteUserUseCase.java` | Inbound port |
| `user/src/main/java/com/skyflux/kiln/user/application/usecase/ListUsersService.java` | Use case impl |
| `user/src/main/java/com/skyflux/kiln/user/application/usecase/UpdateUserService.java` | Use case impl |
| `user/src/main/java/com/skyflux/kiln/user/application/usecase/DeleteUserService.java` | Use case impl |

### Modified files
| File | Change |
|------|--------|
| `user/src/main/java/com/skyflux/kiln/user/domain/model/User.java` | Add `status` field + `deactivate()` method |
| `user/src/main/java/com/skyflux/kiln/user/application/port/out/UserRepository.java` | Add `listByTenant`, `update`, `delete` |
| `user/src/main/java/com/skyflux/kiln/user/adapter/out/persistence/UserMapper.java` | Map `status` column |
| `user/src/main/java/com/skyflux/kiln/user/adapter/out/persistence/UserJooqRepositoryAdapter.java` | Implement new repo methods |
| `user/src/main/java/com/skyflux/kiln/user/adapter/in/web/UserController.java` | Add GET /users, PUT /users/{id}, DELETE /users/{id} |
| `infra/build.gradle` | Add V8 to jOOQ scripts list |

---

## Task 1 — Flyway migration: users.status

- [ ] Create `V8__users_status.sql`:
```sql
-- V8__users_status.sql
ALTER TABLE users ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
COMMENT ON COLUMN users.status IS 'User lifecycle state: ACTIVE | INACTIVE. Soft-delete via INACTIVE.';
```

- [ ] Add `V8__users_status.sql` to the jOOQ scripts list in `infra/build.gradle` (append after `V7__tenant.sql`).

- [ ] Regenerate jOOQ: `./gradlew :infra:generateJooq`
  Expected: `UsersRecord` gains `getStatus()` / `setStatus(String)`.

- [ ] Commit:
```bash
git add infra/
git commit -m "✨ add users.status column for soft-delete lifecycle"
```

---

## Task 2 — Add `status` to User domain + `deactivate()` behaviour (TDD)

- [ ] Write failing test in `UserTest` (add to existing file):
```java
@Test void shouldDeactivateUser() {
    UUID tenantId = Ids.next();
    User user = User.register(tenantId, "Alice", "alice@example.com", "hash");
    assertThat(user.status()).isEqualTo("ACTIVE");
    User deactivated = user.deactivate();
    assertThat(deactivated.status()).isEqualTo("INACTIVE");
}

@Test void shouldRejectDeactivatingAlreadyInactiveUser() {
    UUID tenantId = Ids.next();
    User user = User.register(tenantId, "Alice", "alice@example.com", "hash");
    User inactive = user.deactivate();
    assertThatIllegalStateException().isThrownBy(inactive::deactivate);
}
```

- [ ] Run: `./gradlew :user:test --tests 'com.skyflux.kiln.user.domain.model.UserTest'` — FAIL.

- [ ] Update `User.java`:
  - Add `private final String status;` field.
  - Update private constructor, `register()`, `reconstitute()` to include `status`.
  - `register()` sets `status = "ACTIVE"`.
  - `reconstitute()` signature: add `String status` parameter.
  - Add method:
```java
public String status() { return status; }

public User deactivate() {
    if ("INACTIVE".equals(status)) throw new IllegalStateException("User already inactive");
    return new User(id, tenantId, name, email, passwordHash, failedLoginAttempts, lockedUntil, "INACTIVE");
}
```
  - Update `registerLoginFailure()` and `registerLoginSuccess()` to preserve `status`.

- [ ] Update all call sites of `User.reconstitute()` and tests that construct `User`.

- [ ] Run: `./gradlew :user:test` — all green.

- [ ] Commit: `git commit -m "✅ add User.status field and deactivate() behaviour"`

---

## Task 3 — New use cases: ListUsers, UpdateUser, DeleteUser (TDD)

For each use case, follow the pattern: write failing test → implement → run → green.

### ListUsersUseCase

- [ ] Create port:
```java
// user/src/main/java/com/skyflux/kiln/user/application/port/in/ListUsersUseCase.java
package com.skyflux.kiln.user.application.port.in;

import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import com.skyflux.kiln.user.domain.model.User;

public interface ListUsersUseCase {
    PageResult<User> execute(PageQuery query);
}
```

- [ ] Add to `UserRepository`:
```java
PageResult<User> listActive(PageQuery query);
```

- [ ] Write failing test `ListUsersServiceTest`:
```java
@ExtendWith(MockitoExtension.class)
class ListUsersServiceTest {
    @Mock UserRepository repo;
    @InjectMocks ListUsersService service;

    @Test void shouldDelegateToRepository() {
        PageQuery query = new PageQuery(1, 10);
        PageResult<User> page = PageResult.empty();
        when(repo.listActive(query)).thenReturn(page);
        assertThat(service.execute(query)).isEqualTo(page);
    }
}
```

- [ ] Implement `ListUsersService`:
```java
@Service
class ListUsersService implements ListUsersUseCase {
    private final UserRepository repo;
    ListUsersService(UserRepository repo) { this.repo = repo; }
    @Override
    public PageResult<User> execute(PageQuery query) { return repo.listActive(query); }
}
```

### UpdateUserUseCase

- [ ] Create port:
```java
public interface UpdateUserUseCase {
    record Command(UserId userId, String name) {}
    User execute(Command cmd);
}
```

- [ ] Add to `UserRepository`: `void update(User user);`

- [ ] Write failing test `UpdateUserServiceTest`:
```java
@Test void shouldUpdateName() {
    UserId id = new UserId(Ids.next());
    UUID tenantId = Ids.next();
    User existing = User.reconstitute(id, tenantId, "Old Name", "u@e.com", "h", 0, null, "ACTIVE");
    when(repo.findById(id)).thenReturn(Optional.of(existing));
    User result = service.execute(new UpdateUserUseCase.Command(id, "New Name"));
    assertThat(result.name()).isEqualTo("New Name");
    verify(repo).save(argThat(u -> "New Name".equals(u.name())));
}
```

- [ ] Implement `UpdateUserService`.

### DeleteUserUseCase

- [ ] Create port:
```java
public interface DeleteUserUseCase {
    void execute(UserId userId);
}
```

- [ ] Write failing test, implement `DeleteUserService` (calls `user.deactivate()` then `repo.save()`).

- [ ] Run all: `./gradlew :user:test` — green.

- [ ] Commit: `git commit -m "✅ add ListUsers, UpdateUser, DeleteUser use cases"`

---

## Task 4 — Repository implementations + controller endpoints (TDD)

### Implement `UserRepository.listActive` in `UserJooqRepositoryAdapter`

- [ ] Write failing test in `UserJooqRepositoryAdapterTest`:
```java
@Test void shouldListActiveUsersPagedForCurrentTenant() {
    // Requires Testcontainers — extend existing adapter test class
    // Insert 3 active users and 1 inactive user.
    // listActive(PageQuery(1,2)) should return 2 users.
    // listActive(PageQuery(2,2)) should return 1 user.
    // The inactive user must NOT appear.
}
```

- [ ] Implement in `UserJooqRepositoryAdapter`:
```java
@Override
public PageResult<User> listActive(PageQuery query) {
    int offset = (query.page() - 1) * query.size();
    var condition = Tables.USERS.STATUS.eq("ACTIVE");
    long total = dsl.selectCount().from(Tables.USERS).where(condition)
            .fetchOne(0, Long.class);
    List<User> items = dsl.selectFrom(Tables.USERS)
            .where(condition)
            .orderBy(Tables.USERS.CREATED_AT.desc())
            .limit(query.size()).offset(offset)
            .fetch()
            .map(mapper::toAggregate);
    return PageResult.of(items, total, query.page(), query.size());
}
```

Note: RLS automatically filters by tenant_id — no explicit WHERE tenant_id needed.

### Add REST endpoints to `UserController`

Current endpoints: `GET /{id}`, `POST /` (register).  
Add:

- `GET /api/v1/users` — paginated list (requires auth: `@SaCheckLogin`)
- `PUT /api/v1/users/{id}` — update name (requires auth + owns resource or ADMIN)
- `DELETE /api/v1/users/{id}` — soft delete (`@SaCheckRole("ADMIN")`)

- [ ] Update `UserController.java`:
```java
@GetMapping
@SaCheckLogin
public R<PageResult<UserResponse>> list(@Valid PageQuery query) {
    return R.ok(listUsersUseCase.execute(query).map(UserResponse::from));
}

@PutMapping("/{id}")
@SaCheckLogin
public R<UserResponse> update(@PathVariable UUID id,
                              @Valid @RequestBody UpdateUserRequest req) {
    User user = updateUserUseCase.execute(
        new UpdateUserUseCase.Command(new UserId(id), req.name()));
    return R.ok(UserResponse.from(user));
}

@DeleteMapping("/{id}")
@SaCheckRole("ADMIN")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void delete(@PathVariable UUID id) {
    deleteUserUseCase.execute(new UserId(id));
}
```

- [ ] Add `UpdateUserRequest` record to `UserController`:
```java
public record UpdateUserRequest(@NotBlank @Size(max = 100) String name) {}
```

- [ ] Write `UserControllerTest` slices for new endpoints (follow existing test class pattern with `@WebMvcTest`).

- [ ] Run: `./gradlew :user:test` — all green.

- [ ] Run: `./gradlew check` — all modules green.

- [ ] Run code review: invoke `superpowers:requesting-code-review`. Fix all findings.

- [ ] Commit:
```bash
git add .
git commit -m "✨ add User list, update, and soft-delete endpoints; tenant-scoped via RLS"
```

- [ ] Update OpenAPI snapshot: `./gradlew :app:updateOpenApiSnapshot` then commit snapshot.
