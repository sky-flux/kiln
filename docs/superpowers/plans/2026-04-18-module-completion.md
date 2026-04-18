# Module Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use `- [ ]` syntax for tracking.

**Goal:** Complete three cross-cutting enhancements: Order PAID status + `OrderPaid` event, Member auto-points on payment, and dual-layer audit (HTTP-level AOP + business domain events) with `resource+action` model replacing the old `type` enum.

**Architecture:** Event spine is `OrderEvent.OrderPaid` published by `PayOrderService` after commit → consumed by `OrderPaidMemberListener` (points) and `OrderAuditListener` (audit). HTTP-level `AuditAspect` (@Around all @RestController methods) provides automatic coverage of every API call without manual instrumentation.

**Tech Stack:** Java 25, Spring Boot 4, Spring AOP, jOOQ 3.20, Flyway, Spring Modulith events, `@TransactionalEventListener(AFTER_COMMIT)`.

**Prerequisites:** Run `./gradlew check` before starting — all 553 tests must be green.

---

## File Map

### New files
| File | Role |
|------|------|
| `infra/src/main/resources/db/migration/V14__audit_resource_action.sql` | Replace `audits.type` with `resource` + `action` |
| `audit/src/main/java/com/skyflux/kiln/audit/domain/AuditResource.java` | Enum: USER, TENANT, ROLE, ORDER, PRODUCT, MEMBER, SYSTEM |
| `audit/src/main/java/com/skyflux/kiln/audit/domain/AuditAction.java` | Enum: CREATE, READ, UPDATE, DELETE, LOGIN, LOGOUT, PAY, CANCEL, ASSIGN, REVOKE, AWARD_POINTS |
| `audit/src/main/java/com/skyflux/kiln/audit/internal/AuditAspect.java` | @Around all @RestController methods |
| `audit/src/main/java/com/skyflux/kiln/audit/internal/OrderAuditListener.java` | Audit ORDER_CREATED, ORDER_PAID, ORDER_CANCELLED |
| `audit/src/main/java/com/skyflux/kiln/audit/internal/TenantAuditListener.java` | Audit TENANT_CREATED, TENANT_SUSPENDED |
| `audit/src/main/java/com/skyflux/kiln/audit/internal/ProductAuditListener.java` | Audit PRODUCT_CREATED |
| `audit/src/main/java/com/skyflux/kiln/audit/internal/MemberAuditListener.java` | Audit MEMBER_POINTS_AWARDED |
| `order/src/main/java/com/skyflux/kiln/order/api/OrderEvent.java` | Public OrderPaid event (moved from domain.event) |
| `order/src/main/java/com/skyflux/kiln/order/api/package-info.java` | Public API package |
| `order/src/main/java/com/skyflux/kiln/order/application/port/in/PayOrderUseCase.java` | Inbound port |
| `order/src/main/java/com/skyflux/kiln/order/application/usecase/PayOrderService.java` | CONFIRMED → PAID, publishes OrderPaid |
| `member/src/main/java/com/skyflux/kiln/member/internal/OrderPaidMemberListener.java` | Awards points after OrderPaid commits |

### Modified files
| File | Change |
|------|--------|
| `infra/build.gradle` | Add V14 to jOOQ scripts glob (automatic — glob `V*.sql` covers it) |
| `audit/build.gradle` | Add `spring-boot-starter-aop` |
| `audit/src/main/java/com/skyflux/kiln/audit/domain/Audit.java` | Replace `AuditType type` with `AuditResource resource, AuditAction action` |
| `audit/src/main/java/com/skyflux/kiln/audit/api/AuditService.java` | Replace `AuditType` param with `AuditResource`, `AuditAction` |
| `audit/src/main/java/com/skyflux/kiln/audit/internal/AuditServiceImpl.java` | Same signature change |
| `audit/src/main/java/com/skyflux/kiln/audit/repo/AuditRepository.java` | Replace filter params |
| `audit/src/main/java/com/skyflux/kiln/audit/internal/AuditJooqRepositoryImpl.java` | Use RESOURCE+ACTION columns, drop TYPE |
| `audit/src/main/java/com/skyflux/kiln/audit/api/AuditQueryService.java` | Update filter params |
| `audit/src/main/java/com/skyflux/kiln/audit/internal/AuditQueryServiceImpl.java` | Same |
| `audit/src/main/java/com/skyflux/kiln/audit/internal/web/AuditQueryController.java` | Update request params |
| `audit/src/main/java/com/skyflux/kiln/audit/internal/LoginAuditListener.java` | Use AuditResource.USER + AuditAction.LOGIN |
| `audit/src/main/java/com/skyflux/kiln/audit/internal/RoleAuditListener.java` | Use ROLE + ASSIGN/REVOKE |
| `audit/src/main/java/com/skyflux/kiln/audit/internal/UserLifecycleAuditListener.java` | Use USER + CREATE/UPDATE |
| `audit/src/main/java/com/skyflux/kiln/audit/domain/AuditType.java` | DELETE this file |
| `audit/**Test*.java` | Update all audit tests to new model |
| `order/src/main/java/com/skyflux/kiln/order/domain/model/OrderStatus.java` | Add PAID |
| `order/src/main/java/com/skyflux/kiln/order/domain/model/Order.java` | Add `pay()`, update `cancel()` guard |
| `order/src/main/java/com/skyflux/kiln/order/domain/event/OrderEvent.java` | Keep for backward compat during migration; add `OrderPaid` here too pointing to api version |
| `order/src/main/java/com/skyflux/kiln/order/adapter/in/web/OrderController.java` | Add `POST /{id}/pay` |
| `order/src/main/java/com/skyflux/kiln/order/package-info.java` | Add `order.api` to allowedDependencies if needed |
| `member/build.gradle` | Add `implementation project(':order')` |
| `member/src/main/java/com/skyflux/kiln/member/package-info.java` | Add `order` to allowedDependencies |

---

## Task 1 — V14 Flyway migration: audits table resource+action columns

**Files:**
- Create: `infra/src/main/resources/db/migration/V14__audit_resource_action.sql`

- [ ] Create `V14__audit_resource_action.sql`:

```sql
-- V14__audit_resource_action.sql
-- Replace single `type` column with `resource` + `action` for richer, extensible audit semantics.
-- resource: the entity being acted upon (USER, ORDER, PRODUCT, etc.)
-- action:   the operation performed  (CREATE, LOGIN, PAY, etc.)

ALTER TABLE audits ADD COLUMN resource VARCHAR(50);
ALTER TABLE audits ADD COLUMN action   VARCHAR(50);

UPDATE audits SET
    resource = CASE type
        WHEN 'USER_REGISTERED' THEN 'USER'
        WHEN 'LOGIN_SUCCESS'   THEN 'USER'
        WHEN 'LOGIN_FAILED'    THEN 'USER'
        WHEN 'ACCOUNT_LOCKED'  THEN 'USER'
        WHEN 'ROLE_ASSIGNED'   THEN 'ROLE'
        WHEN 'ROLE_REVOKED'    THEN 'ROLE'
        ELSE 'SYSTEM'
    END,
    action = CASE type
        WHEN 'USER_REGISTERED' THEN 'CREATE'
        WHEN 'LOGIN_SUCCESS'   THEN 'LOGIN'
        WHEN 'LOGIN_FAILED'    THEN 'LOGIN'
        WHEN 'ACCOUNT_LOCKED'  THEN 'UPDATE'
        WHEN 'ROLE_ASSIGNED'   THEN 'ASSIGN'
        WHEN 'ROLE_REVOKED'    THEN 'REVOKE'
        ELSE 'CREATE'
    END;

ALTER TABLE audits ALTER COLUMN resource SET NOT NULL;
ALTER TABLE audits ALTER COLUMN action   SET NOT NULL;
ALTER TABLE audits DROP COLUMN type;
```

- [ ] Regenerate jOOQ (V*.sql glob auto-picks V14):
```bash
./gradlew :infra:generateJooq 2>&1 | grep -E "WARNING|ERROR|BUILD"
```
Expected: BUILD SUCCESSFUL, no warnings. Verify `AuditsRecord` now has `getResource()` / `getAction()` and no `getType()`.

- [ ] Commit:
```bash
git add infra/
git commit -m "✨ add audits.resource and audits.action columns, drop type column (V14)"
```

---

## Task 2 — Audit domain model: AuditResource + AuditAction enums + Audit record

**Files:**
- Create: `audit/src/main/java/com/skyflux/kiln/audit/domain/AuditResource.java`
- Create: `audit/src/main/java/com/skyflux/kiln/audit/domain/AuditAction.java`
- Modify: `audit/src/main/java/com/skyflux/kiln/audit/domain/Audit.java`
- Delete: `audit/src/main/java/com/skyflux/kiln/audit/domain/AuditType.java`

- [ ] Write failing test (add to `AuditTest.java`):
```java
@Test void shouldRequireResourceAndAction() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Audit(UUID.randomUUID(), Instant.now(),
            null, AuditAction.CREATE, null, null, null, null));
    assertThatNullPointerException()
        .isThrownBy(() -> new Audit(UUID.randomUUID(), Instant.now(),
            AuditResource.USER, null, null, null, null, null));
}
@Test void createFactoryShouldUseClockAndGenerateId() {
    Clock fixed = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    Audit a = Audit.create(fixed, AuditResource.USER, AuditAction.LOGIN, null, null, null, null);
    assertThat(a.occurredAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    assertThat(a.resource()).isEqualTo(AuditResource.USER);
    assertThat(a.action()).isEqualTo(AuditAction.LOGIN);
}
```

- [ ] Run: `./gradlew :audit:test --tests '*.AuditTest'` — FAIL.

- [ ] Create `AuditResource.java`:
```java
package com.skyflux.kiln.audit.domain;

public enum AuditResource {
    USER, TENANT, ROLE, ORDER, PRODUCT, MEMBER, SYSTEM
}
```

- [ ] Create `AuditAction.java`:
```java
package com.skyflux.kiln.audit.domain;

public enum AuditAction {
    CREATE, READ, UPDATE, DELETE,
    LOGIN, LOGOUT,
    PAY, CANCEL,
    ASSIGN, REVOKE,
    AWARD_POINTS
}
```

- [ ] Replace `Audit.java` completely:
```java
package com.skyflux.kiln.audit.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Audit(
        UUID id,
        Instant occurredAt,
        AuditResource resource,
        AuditAction action,
        UUID actorUserId,
        UUID targetUserId,
        String details,
        String requestId
) {
    public Audit {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(action, "action");
    }

    public static Audit create(Clock clock, AuditResource resource, AuditAction action,
                                UUID actorUserId, UUID targetUserId,
                                String details, String requestId) {
        Objects.requireNonNull(clock, "clock");
        return new Audit(UUID.randomUUID(), clock.instant(), resource, action,
                         actorUserId, targetUserId, details, requestId);
    }
}
```

- [ ] Delete `AuditType.java`:
```bash
rm audit/src/main/java/com/skyflux/kiln/audit/domain/AuditType.java
```

- [ ] Run: `./gradlew :audit:compileJava 2>&1 | tail -20` — expect compile errors showing all usages to fix.

- [ ] Fix all compile errors in audit module source (listeners, service, repo — tasks 3 and 4 will finish these; fix the minimum needed to compile here).

- [ ] Run: `./gradlew :audit:test --tests '*.AuditTest'` — PASS.

- [ ] Commit:
```bash
git add audit/
git commit -m "✅ replace AuditType with AuditResource + AuditAction enums; update Audit record"
```

---

## Task 3 — Audit service, repository, and query API — update signatures

**Files:**
- Modify: `audit/src/main/java/com/skyflux/kiln/audit/api/AuditService.java`
- Modify: `audit/src/main/java/com/skyflux/kiln/audit/internal/AuditServiceImpl.java`
- Modify: `audit/src/main/java/com/skyflux/kiln/audit/repo/AuditRepository.java`
- Modify: `audit/src/main/java/com/skyflux/kiln/audit/internal/AuditJooqRepositoryImpl.java`
- Modify: `audit/src/main/java/com/skyflux/kiln/audit/api/AuditQueryService.java`
- Modify: `audit/src/main/java/com/skyflux/kiln/audit/internal/AuditQueryServiceImpl.java`
- Modify: `audit/src/main/java/com/skyflux/kiln/audit/internal/web/AuditQueryController.java`

- [ ] Update `AuditService.java`:
```java
package com.skyflux.kiln.audit.api;

import com.skyflux.kiln.audit.domain.Audit;
import com.skyflux.kiln.audit.domain.AuditAction;
import com.skyflux.kiln.audit.domain.AuditResource;
import java.util.UUID;

public interface AuditService {
    Audit record(AuditResource resource, AuditAction action,
                 UUID actorUserId, UUID targetUserId,
                 String details, String requestId);
}
```

- [ ] Update `AuditServiceImpl.java`:
```java
package com.skyflux.kiln.audit.internal;

import com.skyflux.kiln.audit.api.AuditService;
import com.skyflux.kiln.audit.domain.Audit;
import com.skyflux.kiln.audit.domain.AuditAction;
import com.skyflux.kiln.audit.domain.AuditResource;
import com.skyflux.kiln.audit.repo.AuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
class AuditServiceImpl implements AuditService {

    private final Clock clock;
    private final AuditRepository repo;

    AuditServiceImpl(Clock clock, AuditRepository repo) {
        this.clock = clock;
        this.repo = repo;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Audit record(AuditResource resource, AuditAction action,
                        UUID actorUserId, UUID targetUserId,
                        String details, String requestId) {
        Audit audit = Audit.create(clock, resource, action, actorUserId, targetUserId, details, requestId);
        repo.save(audit);
        return audit;
    }
}
```

- [ ] Update `AuditRepository.java`:
```java
package com.skyflux.kiln.audit.repo;

import com.skyflux.kiln.audit.domain.Audit;
import com.skyflux.kiln.audit.domain.AuditAction;
import com.skyflux.kiln.audit.domain.AuditResource;
import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import java.util.UUID;

public interface AuditRepository {
    void save(Audit audit);
    PageResult<Audit> list(PageQuery page, AuditResource resource, AuditAction action,
                           UUID actorUserId, UUID targetUserId);
    long count(AuditResource resource, AuditAction action, UUID actorUserId, UUID targetUserId);
}
```

- [ ] Update `AuditJooqRepositoryImpl.java` — replace all `Tables.AUDITS.TYPE` references with `Tables.AUDITS.RESOURCE` and `Tables.AUDITS.ACTION`:

Key changes in `save()`:
```java
// Remove: .set(Tables.AUDITS.TYPE, audit.type().name())
// Add:
.set(Tables.AUDITS.RESOURCE, audit.resource().name())
.set(Tables.AUDITS.ACTION, audit.action().name())
```

Key changes in `toDomain()` (or equivalent mapping):
```java
// Remove: AuditType.valueOf(r.getType())
// Add:
AuditResource.valueOf(r.getResource())
AuditAction.valueOf(r.getAction())
```

Key changes in `buildWhere()`:
```java
private static Condition buildWhere(AuditResource resource, AuditAction action,
                                     UUID actorUserId, UUID targetUserId) {
    Condition where = DSL.noCondition();
    if (resource != null)     where = where.and(Tables.AUDITS.RESOURCE.eq(resource.name()));
    if (action != null)       where = where.and(Tables.AUDITS.ACTION.eq(action.name()));
    if (actorUserId != null)  where = where.and(Tables.AUDITS.ACTOR_USER_ID.eq(actorUserId));
    if (targetUserId != null) where = where.and(Tables.AUDITS.TARGET_USER_ID.eq(targetUserId));
    return where;
}
```

- [ ] Update `AuditQueryService.java` — replace `AuditType type` param with `AuditResource resource, AuditAction action`.

- [ ] Update `AuditQueryServiceImpl.java` — same param change, delegate to repo.

- [ ] Update `AuditQueryController.java` — replace `@RequestParam AuditType type` with `@RequestParam(required=false) AuditResource resource, @RequestParam(required=false) AuditAction action`.

- [ ] Run: `./gradlew :audit:compileJava :audit:compileTestJava` — should compile clean.

- [ ] Update all audit tests to use new signatures (remove `AuditType` imports, use `AuditResource` + `AuditAction`).

- [ ] Run: `./gradlew :audit:test` — all GREEN.

- [ ] Commit:
```bash
git add audit/
git commit -m "✅ update AuditService, AuditRepository, AuditQueryService to resource+action model"
```

---

## Task 4 — Migrate existing audit listeners

**Files:**
- Modify: `audit/src/main/java/com/skyflux/kiln/audit/internal/LoginAuditListener.java`
- Modify: `audit/src/main/java/com/skyflux/kiln/audit/internal/RoleAuditListener.java`
- Modify: `audit/src/main/java/com/skyflux/kiln/audit/internal/UserLifecycleAuditListener.java`

For each listener, replace `AuditType.XYZ` with `AuditResource.X, AuditAction.Y`:

**`LoginAuditListener.java`** mapping:
```java
// LOGIN_SUCCESS → resource=USER, action=LOGIN, details={"result":"SUCCESS"}
// LOGIN_FAILED  → resource=USER, action=LOGIN, details={"result":"FAILED","reason":event.reason()}
// ACCOUNT_LOCKED → resource=USER, action=UPDATE, details={"reason":"ACCOUNT_LOCKED"}
```

**`RoleAuditListener.java`** mapping:
```java
// ROLE_ASSIGNED → resource=ROLE, action=ASSIGN
// ROLE_REVOKED  → resource=ROLE, action=REVOKE
```

**`UserLifecycleAuditListener.java`** mapping:
```java
// USER_REGISTERED → resource=USER, action=CREATE
```

- [ ] Update all three listener files with new `auditService.record(resource, action, ...)` calls.

- [ ] Run: `./gradlew :audit:test` — all GREEN.

- [ ] Run: `./gradlew check 2>&1 | tail -5` — BUILD SUCCESSFUL.

- [ ] Commit:
```bash
git add audit/
git commit -m "✅ migrate LoginAuditListener, RoleAuditListener, UserLifecycleAuditListener to resource+action model"
```

---

## Task 5 — Order: add PAID status + pay() + OrderPaid event

**Files:**
- Modify: `order/src/main/java/com/skyflux/kiln/order/domain/model/OrderStatus.java`
- Modify: `order/src/main/java/com/skyflux/kiln/order/domain/model/Order.java`
- Create: `order/src/main/java/com/skyflux/kiln/order/api/OrderEvent.java`
- Create: `order/src/main/java/com/skyflux/kiln/order/api/package-info.java`
- Modify: `order/src/main/java/com/skyflux/kiln/order/domain/event/OrderEvent.java`
- Modify: `order/src/main/java/com/skyflux/kiln/order/package-info.java`

- [ ] Write failing test (add to `OrderTest.java`):
```java
@Test void shouldPayConfirmedOrder() {
    Order confirmed = sampleOrder().confirm();
    Order paid = confirmed.pay();
    assertThat(paid.status()).isEqualTo(OrderStatus.PAID);
}
@Test void shouldRejectPayingNonConfirmedOrder() {
    assertThatThrownBy(() -> sampleOrder().pay())
        .isInstanceOf(InvalidOrderTransitionException.class);
}
@Test void shouldNotCancelPaidOrder() {
    assertThatThrownBy(() -> sampleOrder().confirm().pay().cancel())
        .isInstanceOf(InvalidOrderTransitionException.class);
}
@Test void shouldShipPaidOrder() {
    Order shipped = sampleOrder().confirm().pay().ship();
    assertThat(shipped.status()).isEqualTo(OrderStatus.SHIPPED);
}
```

- [ ] Run: `./gradlew :order:test --tests '*.OrderTest'` — FAIL.

- [ ] Update `OrderStatus.java`:
```java
public enum OrderStatus { PENDING, CONFIRMED, PAID, SHIPPED, DELIVERED, CANCELLED }
```

- [ ] Update `Order.java` — add `pay()` and update `cancel()` guard:
```java
public Order pay()    { return transition(OrderStatus.PAID, OrderStatus.CONFIRMED); }
public Order ship()   { return transition(OrderStatus.SHIPPED, OrderStatus.PAID); }   // ship only after PAID
// Update cancel: only PENDING or CONFIRMED can be cancelled
public Order cancel() { return transition(OrderStatus.CANCELLED, OrderStatus.PENDING, OrderStatus.CONFIRMED); }
```

Note: `ship()` currently accepts `CONFIRMED` — after adding `PAID`, shipping should require `PAID`. Update the test `shouldShipConfirmedOrder` to `shouldShipPaidOrder`.

- [ ] Run: `./gradlew :order:test --tests '*.OrderTest'` — PASS.

- [ ] Create `order/src/main/java/com/skyflux/kiln/order/api/package-info.java`:
```java
/** Public API types exposed to other modules (e.g. member listens to OrderPaid). */
package com.skyflux.kiln.order.api;
```

- [ ] Create `order/src/main/java/com/skyflux/kiln/order/api/OrderEvent.java`:
```java
package com.skyflux.kiln.order.api;

import com.skyflux.kiln.common.money.Money;
import com.skyflux.kiln.order.domain.model.OrderId;

import java.time.Instant;
import java.util.UUID;

/**
 * Public domain events published by the order module.
 * Placed in order.api so other modules (member, audit) can listen
 * without crossing into order.domain.
 */
public sealed interface OrderEvent {
    record OrderCreated(OrderId orderId, UUID tenantId, UUID userId, Instant occurredAt)
        implements OrderEvent {}
    record OrderPaid(OrderId orderId, UUID tenantId, UUID userId, Money amount, Instant occurredAt)
        implements OrderEvent {}
    record OrderCancelled(OrderId orderId, UUID tenantId, Instant occurredAt)
        implements OrderEvent {}
}
```

- [ ] Update `order/src/main/java/com/skyflux/kiln/order/domain/event/OrderEvent.java` to re-export (or simply delete and fix imports — whichever is cleaner). **Simplest approach: delete `order/domain/event/OrderEvent.java` and update all imports across the `order` module to use `order.api.OrderEvent`.**

- [ ] Update `order/src/main/java/com/skyflux/kiln/order/package-info.java` — ensure `order.api` is in the exposed package list (Modulith `namedInterfaces` or just leave as default since it's in a sub-package):
```java
@org.springframework.modulith.ApplicationModule(
    displayName = "Order",
    allowedDependencies = {"common", "infra", "tenant"}
)
package com.skyflux.kiln.order;
```
Spring Modulith auto-exposes the `api` sub-package — no extra config needed.

- [ ] Fix all compile errors (imports of old `order.domain.event.OrderEvent` → `order.api.OrderEvent`) in: `CreateOrderService`, `CancelOrderService`, `OrderAuditListener` (if it exists), test files.

- [ ] Run: `./gradlew :order:test` — all GREEN.

- [ ] Commit:
```bash
git add order/
git commit -m "✅ add OrderStatus.PAID, Order.pay(), OrderPaid event in order.api"
```

---

## Task 6 — PayOrderUseCase + PayOrderService + controller endpoint

**Files:**
- Create: `order/src/main/java/com/skyflux/kiln/order/application/port/in/PayOrderUseCase.java`
- Create: `order/src/main/java/com/skyflux/kiln/order/application/usecase/PayOrderService.java`
- Modify: `order/src/main/java/com/skyflux/kiln/order/adapter/in/web/OrderController.java`
- Create: `order/src/test/java/com/skyflux/kiln/order/application/usecase/PayOrderServiceTest.java`

- [ ] Create `PayOrderUseCase.java`:
```java
package com.skyflux.kiln.order.application.port.in;

import com.skyflux.kiln.order.domain.model.Order;
import com.skyflux.kiln.order.domain.model.OrderId;

public interface PayOrderUseCase {
    Order execute(OrderId id);
}
```

- [ ] Write failing `PayOrderServiceTest.java`:
```java
package com.skyflux.kiln.order.application.usecase;

import com.skyflux.kiln.common.money.Money;
import com.skyflux.kiln.order.api.OrderEvent;
import com.skyflux.kiln.order.application.port.in.PayOrderUseCase;
import com.skyflux.kiln.order.application.port.out.OrderRepository;
import com.skyflux.kiln.order.domain.model.*;
import com.skyflux.kiln.common.util.Ids;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayOrderServiceTest {

    @Mock OrderRepository repo;
    @Mock ApplicationEventPublisher events;
    @Mock Clock clock;
    @InjectMocks PayOrderService service;

    private Order confirmedOrder() {
        OrderItem item = new OrderItem(OrderItemId.newId(), UUID.randomUUID(),
            "SKU-1", "Widget", Money.of("100.00", "CNY"), 1, Money.of("100.00", "CNY"));
        return Order.create(Ids.next(), Ids.next(), List.of(item), null).confirm();
    }

    @Test void shouldTransitionConfirmedToPaid() {
        Order order = confirmedOrder();
        when(repo.findById(order.id())).thenReturn(Optional.of(order));
        when(clock.instant()).thenReturn(java.time.Instant.now());

        Order result = service.execute(order.id());

        assertThat(result.status()).isEqualTo(OrderStatus.PAID);
        verify(repo).save(argThat(o -> o.status() == OrderStatus.PAID));
    }

    @Test void shouldPublishOrderPaidEvent() {
        Order order = confirmedOrder();
        when(repo.findById(order.id())).thenReturn(Optional.of(order));
        when(clock.instant()).thenReturn(java.time.Instant.now());

        service.execute(order.id());

        ArgumentCaptor<OrderEvent.OrderPaid> cap = ArgumentCaptor.forClass(OrderEvent.OrderPaid.class);
        verify(events).publishEvent(cap.capture());
        assertThat(cap.getValue().orderId()).isEqualTo(order.id());
        assertThat(cap.getValue().amount().amount()).isEqualByComparingTo("100.00");
    }

    @Test void shouldThrowNotFoundForUnknownOrder() {
        OrderId id = OrderId.newId();
        when(repo.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.execute(id))
            .isInstanceOf(com.skyflux.kiln.common.exception.AppException.class);
    }
}
```

- [ ] Run: `./gradlew :order:test --tests '*.PayOrderServiceTest'` — FAIL.

- [ ] Create `PayOrderService.java`:
```java
package com.skyflux.kiln.order.application.usecase;

import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.order.api.OrderEvent;
import com.skyflux.kiln.order.application.port.in.PayOrderUseCase;
import com.skyflux.kiln.order.application.port.out.OrderRepository;
import com.skyflux.kiln.order.domain.model.Order;
import com.skyflux.kiln.order.domain.model.OrderId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@Transactional
class PayOrderService implements PayOrderUseCase {

    private final OrderRepository repo;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    PayOrderService(OrderRepository repo, ApplicationEventPublisher events, Clock clock) {
        this.repo = repo;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public Order execute(OrderId id) {
        Order order = repo.findById(id)
            .orElseThrow(() -> new AppException(AppCode.NOT_FOUND));
        Order paid = order.pay();
        repo.save(paid);
        events.publishEvent(new OrderEvent.OrderPaid(
            paid.id(), paid.tenantId(), paid.userId(), paid.totalAmount(), clock.instant()));
        return paid;
    }
}
```

- [ ] Run: `./gradlew :order:test --tests '*.PayOrderServiceTest'` — PASS.

- [ ] Add endpoint to `OrderController.java` (inject `PayOrderUseCase`):
```java
@PostMapping("/{id}/pay")
@SaCheckRole("ADMIN")
R<OrderResponse> pay(@PathVariable String id) {
    return R.ok(OrderResponse.from(payUseCase.execute(OrderId.of(id))));
}
```

- [ ] Run: `./gradlew :order:test` — all GREEN.

- [ ] Commit:
```bash
git add order/
git commit -m "✨ add PayOrderUseCase + PayOrderService + POST /orders/{id}/pay endpoint"
```

---

## Task 7 — Member: auto-award points on OrderPaid

**Files:**
- Modify: `member/build.gradle`
- Modify: `member/src/main/java/com/skyflux/kiln/member/package-info.java`
- Create: `member/src/main/java/com/skyflux/kiln/member/internal/OrderPaidMemberListener.java`
- Create: `member/src/test/java/com/skyflux/kiln/member/internal/OrderPaidMemberListenerTest.java`

- [ ] Add to `member/build.gradle` dependencies:
```groovy
implementation project(':order')
```

- [ ] Update `member/src/main/java/com/skyflux/kiln/member/package-info.java`:
```java
@org.springframework.modulith.ApplicationModule(
    displayName = "Member",
    allowedDependencies = {"common", "infra", "tenant", "user", "order"}
)
package com.skyflux.kiln.member;
```

- [ ] Write failing `OrderPaidMemberListenerTest.java`:
```java
package com.skyflux.kiln.member.internal;

import com.skyflux.kiln.common.money.Money;
import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.member.domain.Member;
import com.skyflux.kiln.member.domain.MemberId;
import com.skyflux.kiln.member.domain.MemberLevel;
import com.skyflux.kiln.member.repo.MemberJooqRepository;
import com.skyflux.kiln.order.api.OrderEvent;
import com.skyflux.kiln.order.domain.model.OrderId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderPaidMemberListenerTest {

    @Mock MemberJooqRepository memberRepo;
    @InjectMocks OrderPaidMemberListener listener;

    @Test void shouldAwardPointsEqualToOrderAmountRoundedDown() {
        UUID userId = Ids.next();
        UUID tenantId = Ids.next();
        Member member = Member.reconstitute(MemberId.newId(), tenantId, userId,
            MemberLevel.BRONZE, 0, "ACTIVE");
        when(memberRepo.findByUserId(userId)).thenReturn(Optional.of(member));

        OrderEvent.OrderPaid event = new OrderEvent.OrderPaid(
            OrderId.newId(), tenantId, userId,
            Money.of("250.75", "CNY"), Instant.now());

        listener.on(event);

        verify(memberRepo).save(argThat(m -> m.points() == 250));
    }

    @Test void shouldNoOpWhenMemberNotFound() {
        UUID userId = Ids.next();
        when(memberRepo.findByUserId(userId)).thenReturn(Optional.empty());

        OrderEvent.OrderPaid event = new OrderEvent.OrderPaid(
            OrderId.newId(), Ids.next(), userId,
            Money.of("100.00", "CNY"), Instant.now());

        listener.on(event);  // must not throw

        verify(memberRepo, never()).save(any());
    }
}
```

- [ ] Run: `./gradlew :member:test --tests '*.OrderPaidMemberListenerTest'` — FAIL.

- [ ] Create `OrderPaidMemberListener.java`:
```java
package com.skyflux.kiln.member.internal;

import com.skyflux.kiln.member.domain.Member;
import com.skyflux.kiln.member.repo.MemberJooqRepository;
import com.skyflux.kiln.order.api.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

@Component
class OrderPaidMemberListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidMemberListener.class);

    private final MemberJooqRepository memberRepo;

    OrderPaidMemberListener(MemberJooqRepository memberRepo) {
        this.memberRepo = memberRepo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(OrderEvent.OrderPaid event) {
        Optional<Member> memberOpt = memberRepo.findByUserId(event.userId());
        if (memberOpt.isEmpty()) {
            log.warn("OrderPaidMemberListener: no member found for userId={}, skipping points award",
                event.userId());
            return;
        }
        int points = event.amount().amount().intValue();  // 1 point per 1 CNY, truncated
        Member updated = memberOpt.get().awardPoints(points);
        memberRepo.save(updated);
    }
}
```

- [ ] Run: `./gradlew :member:test --tests '*.OrderPaidMemberListenerTest'` — PASS.

- [ ] Run: `./gradlew :member:test` — all GREEN.

- [ ] Commit:
```bash
git add member/
git commit -m "✨ award member points automatically on OrderPaid event"
```

---

## Task 8 — AuditAspect: HTTP-layer automatic audit

**Files:**
- Modify: `audit/build.gradle`
- Create: `audit/src/main/java/com/skyflux/kiln/audit/internal/AuditAspect.java`
- Create: `audit/src/test/java/com/skyflux/kiln/audit/internal/AuditAspectTest.java`

- [ ] Add to `audit/build.gradle` dependencies:
```groovy
implementation 'org.springframework.boot:spring-boot-starter-aop'
```

- [ ] Write failing `AuditAspectTest.java` (Spring slice test):
```java
package com.skyflux.kiln.audit.internal;

import com.skyflux.kiln.audit.api.AuditService;
import com.skyflux.kiln.audit.domain.AuditAction;
import com.skyflux.kiln.audit.domain.AuditResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditAspectTest {

    @Mock AuditService auditService;
    @InjectMocks AuditAspect aspect;

    private void setRequest(String method, String path) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, path);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    @Test void shouldMapPostOrdersToOrderCreate() {
        setRequest("POST", "/api/v1/orders");
        aspect.recordSuccess(null);
        verify(auditService).record(eq(AuditResource.ORDER), eq(AuditAction.CREATE),
            any(), any(), any(), any());
    }

    @Test void shouldMapPostLoginToUserLogin() {
        setRequest("POST", "/api/v1/auth/login");
        aspect.recordSuccess(null);
        verify(auditService).record(eq(AuditResource.USER), eq(AuditAction.LOGIN),
            any(), any(), any(), any());
    }

    @Test void shouldMapDeleteProductToProductDelete() {
        setRequest("DELETE", "/api/v1/products/some-uuid");
        aspect.recordSuccess(null);
        verify(auditService).record(eq(AuditResource.PRODUCT), eq(AuditAction.DELETE),
            any(), any(), any(), any());
    }
}
```

- [ ] Run: `./gradlew :audit:test --tests '*.AuditAspectTest'` — FAIL.

- [ ] Create `AuditAspect.java`:
```java
package com.skyflux.kiln.audit.internal;

import cn.dev33.satoken.stp.StpUtil;
import com.skyflux.kiln.audit.api.AuditService;
import com.skyflux.kiln.audit.domain.AuditAction;
import com.skyflux.kiln.audit.domain.AuditResource;
import com.skyflux.kiln.tenant.api.TenantContext;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

@Aspect
@Component
class AuditAspect {

    private final AuditService auditService;

    AuditAspect(AuditService auditService) { this.auditService = auditService; }

    @AfterReturning(
        pointcut = "within(@org.springframework.web.bind.annotation.RestController *)",
        returning = "result")
    public void recordSuccess(Object result) {
        HttpServletRequest request = currentRequest();
        if (request == null) return;

        String path   = request.getRequestURI();
        String method = request.getMethod();

        AuditResource resource = resolveResource(path);
        AuditAction   action   = resolveAction(method, path);

        UUID actorId  = resolveActor();
        String requestId = MDC.get("traceId");
        String details = "{\"path\":\"" + path + "\",\"method\":\"" + method + "\"}";

        auditService.record(resource, action, actorId, null, details, requestId);
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes sra)) return null;
        return sra.getRequest();
    }

    private static AuditResource resolveResource(String path) {
        if (path.contains("/auth") || (path.contains("/users") && !path.contains("/members")))
            return AuditResource.USER;
        if (path.contains("/tenants"))  return AuditResource.TENANT;
        if (path.contains("/orders"))   return AuditResource.ORDER;
        if (path.contains("/products")) return AuditResource.PRODUCT;
        if (path.contains("/members"))  return AuditResource.MEMBER;
        if (path.contains("/roles"))    return AuditResource.ROLE;
        return AuditResource.SYSTEM;
    }

    private static AuditAction resolveAction(String method, String path) {
        // Path-segment overrides take priority over HTTP-method mapping
        if (path.endsWith("/login"))    return AuditAction.LOGIN;
        if (path.endsWith("/logout"))   return AuditAction.LOGOUT;
        if (path.endsWith("/pay"))      return AuditAction.PAY;
        if (path.endsWith("/cancel"))   return AuditAction.CANCEL;
        if (path.endsWith("/points"))   return AuditAction.AWARD_POINTS;
        if (path.matches(".*/[^/]+/(confirm|ship|deliver)$")) return AuditAction.UPDATE;
        // HTTP method default
        return switch (method.toUpperCase()) {
            case "GET"    -> AuditAction.READ;
            case "POST"   -> AuditAction.CREATE;
            case "PUT",
                 "PATCH"  -> AuditAction.UPDATE;
            case "DELETE" -> AuditAction.DELETE;
            default       -> AuditAction.CREATE;
        };
    }

    private static UUID resolveActor() {
        try {
            return StpUtil.isLogin() ? UUID.fromString(StpUtil.getLoginIdAsString()) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
```

- [ ] Run: `./gradlew :audit:test --tests '*.AuditAspectTest'` — PASS.

- [ ] Run: `./gradlew :audit:test` — all GREEN.

- [ ] Commit:
```bash
git add audit/
git commit -m "✨ add AuditAspect for automatic HTTP-layer audit of all REST controller calls"
```

---

## Task 9 — New domain event listeners (Order, Tenant, Product, Member)

**Files:**
- Create: `audit/src/main/java/com/skyflux/kiln/audit/internal/OrderAuditListener.java`
- Create: `audit/src/main/java/com/skyflux/kiln/audit/internal/TenantAuditListener.java`
- Create: `audit/src/main/java/com/skyflux/kiln/audit/internal/ProductAuditListener.java`
- Create: `audit/src/main/java/com/skyflux/kiln/audit/internal/MemberAuditListener.java`
- Modify: `audit/build.gradle` — add `order`, `tenant`, `product`, `member` deps
- Modify: `audit/src/main/java/com/skyflux/kiln/audit/package-info.java`

- [ ] Update `audit/build.gradle` — add module dependencies:
```groovy
implementation project(':order')
implementation project(':tenant')
implementation project(':product')
implementation project(':member')
```

Note: These create direct module dependencies. Since audit is a Generic subdomain that observes all domains, this is acceptable. Alternatively, use Spring Modulith `@ApplicationModuleListener` which works cross-module without direct deps — but that requires events to be in public `api` packages, which order.api already satisfies.

- [ ] Update `audit/src/main/java/com/skyflux/kiln/audit/package-info.java`:
```java
@org.springframework.modulith.ApplicationModule(
    displayName = "Audit",
    allowedDependencies = {"common", "infra", "user", "order", "tenant", "product", "member"}
)
package com.skyflux.kiln.audit;
```

- [ ] Create `OrderAuditListener.java`:
```java
package com.skyflux.kiln.audit.internal;

import com.skyflux.kiln.audit.api.AuditService;
import com.skyflux.kiln.audit.domain.AuditAction;
import com.skyflux.kiln.audit.domain.AuditResource;
import com.skyflux.kiln.order.api.OrderEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class OrderAuditListener {

    private final AuditService auditService;
    OrderAuditListener(AuditService auditService) { this.auditService = auditService; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onCreated(OrderEvent.OrderCreated e) {
        auditService.record(AuditResource.ORDER, AuditAction.CREATE,
            e.userId(), null,
            "{\"orderId\":\"" + e.orderId().value() + "\"}", null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onPaid(OrderEvent.OrderPaid e) {
        auditService.record(AuditResource.ORDER, AuditAction.PAY,
            e.userId(), null,
            "{\"orderId\":\"" + e.orderId().value() + "\",\"amount\":\"" + e.amount().amount() + "\"}",
            null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onCancelled(OrderEvent.OrderCancelled e) {
        auditService.record(AuditResource.ORDER, AuditAction.CANCEL,
            null, null,
            "{\"orderId\":\"" + e.orderId().value() + "\"}", null);
    }
}
```

- [ ] Create `TenantAuditListener.java` — listens to tenant domain events (check what events `tenant` module publishes, e.g. `RoleEvent`-style events). If `tenant` module doesn't publish domain events yet, skip this listener (implement when tenant events are added):

Check: `find tenant/src -name "*Event*"` — if no events exist, create placeholder comment class and move on.

- [ ] Create `ProductAuditListener.java` — similar pattern. Check if `product` module publishes events. If not, skip for now.

- [ ] Create `MemberAuditListener.java`:

Check if `member` module has an event for points award. If `MemberService.awardPoints()` doesn't publish an event, add a simple `MemberEvent.PointsAwarded` to `member.api`:

```java
// member/src/main/java/com/skyflux/kiln/member/api/MemberEvent.java
package com.skyflux.kiln.member.api;
import java.time.Instant; import java.util.UUID;
public sealed interface MemberEvent {
    record PointsAwarded(UUID userId, UUID tenantId, int points, Instant occurredAt)
        implements MemberEvent {}
}
```

Then publish it from `OrderPaidMemberListener.on()` after saving (or from `MemberService.awardPoints()`).

Then create `MemberAuditListener`:
```java
@Component
class MemberAuditListener {
    private final AuditService auditService;
    MemberAuditListener(AuditService auditService) { this.auditService = auditService; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onPointsAwarded(MemberEvent.PointsAwarded e) {
        auditService.record(AuditResource.MEMBER, AuditAction.AWARD_POINTS,
            e.userId(), null,
            "{\"points\":" + e.points() + "}", null);
    }
}
```

- [ ] Run: `./gradlew :audit:test` — all GREEN.

- [ ] Run: `./gradlew check 2>&1 | tail -5` — BUILD SUCCESSFUL.

- [ ] Commit:
```bash
git add audit/ member/
git commit -m "✨ add domain event audit listeners for Order, Member operations"
```

---

## Task 10 — Full verification, code review, OpenAPI snapshot, push

- [ ] Run full test suite:
```bash
./gradlew check 2>&1 | tail -10
```
Must be BUILD SUCCESSFUL, 0 failures.

- [ ] Invoke `superpowers:requesting-code-review` skill. Fix all Medium+ findings.

- [ ] Update OpenAPI snapshot:
```bash
./gradlew :app:updateOpenApiSnapshot
git add docs/openapi-snapshot.json
git commit -m "🔧 update OpenAPI snapshot for pay endpoint and audit resource/action params" 2>/dev/null || true
```

- [ ] Push:
```bash
git push origin main
```

- [ ] Report: total test count, any remaining gaps vs spec.
