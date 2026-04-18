# Module Completion Design

**Goal:** Complete three cross-cutting enhancements across existing modules: Order payment status, Member auto points, and dual-layer audit.

**Architecture:** Event-driven integration via Spring Modulith events. All three features connect through `OrderPaid` domain event. Audit is restructured as two layers: HTTP-level AOP (automatic, all operations) + domain-level listeners (business semantics).

---

## 1. Order — PAID Status

### State Machine
```
PENDING → CONFIRMED → PAID → SHIPPED → DELIVERED
              ↘ CANCELLED    (PAID cannot be cancelled)
```

### Changes
- Add `OrderStatus.PAID`
- Add `OrderEvent.OrderPaid(orderId, tenantId, userId, amount, occurredAt)`
- Add `PayOrderUseCase` — transitions `CONFIRMED → PAID`, publishes `OrderPaid`
- Add `PayOrderService` implements `PayOrderUseCase`
- Add `POST /api/v1/orders/{id}/pay` (`@SaCheckRole("ADMIN")`) — placeholder for real payment gateway
- V14 migration: no schema change needed (status stored as VARCHAR)

### Cancel guard
`Order.cancel()` throws `InvalidOrderTransitionException` if status is `PAID`, `SHIPPED`, or `DELIVERED`.

---

## 2. Member — Auto Points on OrderPaid

### Listener
`OrderPaidMemberListener` in `member` module:
- `@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)`
- Listens to `OrderEvent.OrderPaid`
- Points formula: `(int) amount.amount().longValue()` (1 point per 1 CNY, integer truncation)
- Calls `MemberService.awardPoints(userId, points)`
- If member not found: log warn, no-op (user may not have a member profile yet)

### Dependencies
`member/build.gradle` already depends on `order` module via `user` events; add explicit `implementation project(':order')` for `OrderEvent` access.

Wait — cross-module event consumption should use Spring Modulith events, not direct imports. Since `OrderEvent` is in `order.domain.event`, and `member` shouldn't depend on `order` directly per module boundary rules, the event should be moved to `order.api` package (public) so `member` can consume it without violating Modulith boundaries.

**Resolution:** Move `OrderEvent` to `order/src/main/java/com/skyflux/kiln/order/api/OrderEvent.java` and update `order/package-info.java` to expose `order.api`. Member module adds `implementation project(':order')`.

---

## 3. Dual-Layer Audit

### Layer 1 — HTTP AOP (automatic, all REST calls)

**`AuditAspect`** (`@Aspect` bean in `audit.internal`):
- Pointcut: all public methods in `@RestController` beans
- Runs `@Around` the controller method
- Captures: `resource` (from path), `action` (from HTTP method), `actorUserId` (Sa-Token), `tenantId` (TenantContext), `requestId` (MDC), `details` JSON (`{path, httpMethod, status}`)
- On success: `AuditService.record(resource, action, actorId, null, details)`
- On exception: same but `details` includes error code

**Path → Resource mapping (heuristic):**
| Path prefix | Resource |
|---|---|
| `/api/v1/users` | `USER` |
| `/api/v1/auth` | `USER` |
| `/api/v1/tenants` or `/admin/tenants` | `TENANT` |
| `/api/v1/orders` | `ORDER` |
| `/api/v1/products` | `PRODUCT` |
| `/api/v1/members` or `/admin/members` | `MEMBER` |
| `/api/v1/admin/roles` | `ROLE` |
| `/api/v1/admin/users` | `USER` |
| other | `SYSTEM` |

**HTTP method → Action mapping:**
| HTTP Method | Action |
|---|---|
| GET | READ |
| POST | CREATE |
| PUT / PATCH | UPDATE |
| DELETE | DELETE |

Special overrides (by path segment): `/login` → LOGIN, `/logout` → LOGOUT, `/pay` → PAY, `/confirm` → UPDATE, `/cancel` → CANCEL, `/points` → AWARD_POINTS.

### Layer 2 — Domain events (business semantics, selective)

Existing listeners (`LoginAuditListener`, `RoleAuditListener`, `UserLifecycleAuditListener`) remain but migrate to new `resource + action` model.

New listeners:
- `OrderAuditListener` — ORDER_CREATED, ORDER_PAID, ORDER_CANCELLED
- `TenantAuditListener` — TENANT_CREATED, TENANT_SUSPENDED
- `ProductAuditListener` — PRODUCT_CREATED
- `MemberAuditListener` — MEMBER_POINTS_AWARDED

### Audit domain model change

Replace single `type VARCHAR(50)` with `resource VARCHAR(50)` + `action VARCHAR(50)`:

```java
public enum AuditAction {
    CREATE, READ, UPDATE, DELETE, LOGIN, LOGOUT,
    PAY, CANCEL, ASSIGN, REVOKE, AWARD_POINTS
}

public enum AuditResource {
    USER, TENANT, ROLE, ORDER, PRODUCT, MEMBER, SYSTEM
}
```

`Audit` record: replace `AuditType type` with `AuditResource resource, AuditAction action`.

### V14 Migration
```sql
ALTER TABLE audits ADD COLUMN resource VARCHAR(50);
ALTER TABLE audits ADD COLUMN action  VARCHAR(50);
UPDATE audits SET resource = 'SYSTEM', action = 'CREATE' WHERE resource IS NULL;
ALTER TABLE audits ALTER COLUMN resource SET NOT NULL;
ALTER TABLE audits ALTER COLUMN action  SET NOT NULL;
ALTER TABLE audits DROP COLUMN type;
```

---

## Implementation Order

1. **V14 migration** + jOOQ regen
2. **Audit domain model** — replace type with resource+action, migrate existing listeners
3. **Order PAID** — PayOrderUseCase + state machine + OrderEvent.OrderPaid to order.api
4. **Member auto-points** — OrderPaidMemberListener
5. **AuditAspect** — HTTP-layer automatic audit
6. **New domain event listeners** — Order/Tenant/Product/Member audit listeners
7. **Full check + code review + commit**
