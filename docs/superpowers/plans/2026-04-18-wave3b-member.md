# Member Module — Wave 3b Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans`. Steps use `- [ ]` syntax.

**Goal:** Implement the `member` Gradle module — a Supporting subdomain providing tenant-scoped loyalty membership: member profile linked to a `User`, configurable tier levels (BRONZE/SILVER/GOLD), a points balance, and admin endpoints for awarding points and managing members.

**Architecture:** Supporting subdomain → simplified layout (`api/domain/repo/internal/config`). Member is a 1-to-1 extension of a User (one member per user per tenant). A `MemberRegistered` event fires after `UserRegistered` to auto-create the member record. `member.tenantId` is inherited from `user.tenantId` — stored explicitly for RLS. Points system: simple integer balance; earning rules are external (admin awards points via API; event-driven earning is a future concern).

**Tech Stack:** Java 25, Spring Boot 4, jOOQ 3.20, Spring Modulith events.

**Prerequisites:**
- Wave 1 complete (tenant + RLS).
- Wave 2a complete (User CRUD supplement — `UserRegistered` event shape may change).
- Wave 3a (Product) does NOT need to complete before Wave 3b — run in parallel.
- `./gradlew check` all green before starting.

---

## File Map

### New Gradle module: `member/`
```
member/
├── build.gradle
└── src/main/java/com/skyflux/kiln/member/
    ├── package-info.java
    ├── api/MemberSummary.java              ← public projection for other modules
    ├── domain/
    │   ├── Member.java
    │   ├── MemberId.java
    │   └── MemberLevel.java               ← enum: BRONZE, SILVER, GOLD
    ├── repo/MemberJooqRepository.java
    ├── internal/
    │   ├── MemberService.java
    │   ├── MemberController.java
    │   └── UserRegisteredMemberListener.java
    └── config/MemberModuleConfig.java
```

### New migration
| File | Change |
|------|--------|
| `infra/src/main/resources/db/migration/V11__member.sql` | `members` table |
| `infra/src/main/resources/db/migration/R__rls.sql` | Append `members` RLS policy |

### Modified files
| File | Change |
|------|--------|
| `settings.gradle` | Add `include 'member'` |
| `app/build.gradle` | Add `implementation project(':member')` |
| `infra/build.gradle` | Add V11 to jOOQ scripts list |

---

## Task 1 — Flyway migration

- [ ] Create `V11__member.sql`:
```sql
-- V11__member.sql
CREATE TABLE members (
    id          UUID        PRIMARY KEY,
    tenant_id   UUID        NOT NULL REFERENCES tenants(id),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    level       VARCHAR(20) NOT NULL DEFAULT 'BRONZE',
    points      INTEGER     NOT NULL DEFAULT 0 CHECK (points >= 0),
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    joined_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT members_user_tenant_unique UNIQUE (user_id, tenant_id)
);
COMMENT ON TABLE members IS 'Loyalty membership profile. One per user per tenant.';
COMMENT ON COLUMN members.points IS 'Cumulative points balance. Never goes negative.';
```

- [ ] Append to `R__rls.sql`:
```sql
-- members: isolate by tenant_id
ALTER TABLE members ENABLE ROW LEVEL SECURITY;
ALTER TABLE members FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON members;
CREATE POLICY tenant_isolation ON members
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
```

- [ ] Add V11 to jOOQ scripts list in `infra/build.gradle`.

- [ ] Regenerate: `./gradlew :infra:generateJooq`. Expect `Tables.MEMBERS` + `MembersRecord`.

- [ ] Commit: `git commit -m "✨ add members table with loyalty points and tenant RLS"`

---

## Task 2 — Scaffold member module

- [ ] Add `include 'member'` to `settings.gradle`.

- [ ] Create `member/build.gradle`:
```groovy
dependencies {
    implementation project(':common')
    implementation project(':infra')
    implementation project(':tenant')
    implementation project(':user')       // for UserRegistered event
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.modulith:spring-modulith-starter-core'
    implementation 'cn.dev33:sa-token-spring-boot3-starter:1.45.0'

    testImplementation 'org.springframework.boot:spring-boot-testcontainers'
    testImplementation 'org.testcontainers:junit-jupiter:1.21.3'
    testImplementation 'org.testcontainers:postgresql:1.21.3'
    testImplementation 'org.springframework.boot:spring-boot-starter-flyway'
}

jacocoTestCoverageVerification {
    violationRules {
        rule {
            element = 'BUNDLE'
            limit { counter = 'LINE'; minimum = 0.65 }
            limit { counter = 'BRANCH'; minimum = 0.55 }
        }
    }
}
check.dependsOn jacocoTestCoverageVerification
```

- [ ] Add `implementation project(':member')` to `app/build.gradle`.

- [ ] Create `package-info.java`:
```java
@org.springframework.modulith.ApplicationModule(
    displayName = "Member",
    allowedDependencies = {"common", "infra", "tenant", "user"}
)
package com.skyflux.kiln.member;
```

- [ ] Commit: `git commit -m "✨ scaffold member Gradle module"`

---

## Task 3 — Domain model (TDD)

- [ ] Write failing `MemberTest`:
```java
class MemberTest {
    @Test void shouldCreateWithBronzeLevel() {
        Member m = Member.create(Ids.next(), Ids.next());
        assertThat(m.level()).isEqualTo(MemberLevel.BRONZE);
        assertThat(m.points()).isZero();
        assertThat(m.status()).isEqualTo("ACTIVE");
    }

    @Test void shouldAwardPoints() {
        Member m = Member.create(Ids.next(), Ids.next());
        Member updated = m.awardPoints(100);
        assertThat(updated.points()).isEqualTo(100);
    }

    @Test void shouldRejectNegativePointAward() {
        Member m = Member.create(Ids.next(), Ids.next());
        assertThatIllegalArgumentException().isThrownBy(() -> m.awardPoints(-1));
    }

    @Test void shouldUpgradeLevelToSilverAt1000Points() {
        Member m = Member.create(Ids.next(), Ids.next()).awardPoints(1000);
        assertThat(m.level()).isEqualTo(MemberLevel.SILVER);
    }

    @Test void shouldUpgradeLevelToGoldAt5000Points() {
        Member m = Member.create(Ids.next(), Ids.next()).awardPoints(5000);
        assertThat(m.level()).isEqualTo(MemberLevel.GOLD);
    }
}
```

- [ ] Create `MemberLevel.java`:
```java
package com.skyflux.kiln.member.domain;

public enum MemberLevel {
    BRONZE(0), SILVER(1000), GOLD(5000);

    private final int minPoints;
    MemberLevel(int minPoints) { this.minPoints = minPoints; }

    public static MemberLevel forPoints(int points) {
        if (points >= GOLD.minPoints) return GOLD;
        if (points >= SILVER.minPoints) return SILVER;
        return BRONZE;
    }
}
```

- [ ] Create `MemberId.java` (same pattern as `ProductId` / `TenantId`, UUID v7).

- [ ] Create `Member.java`:
```java
package com.skyflux.kiln.member.domain;

import com.skyflux.kiln.common.util.Ids;
import java.util.Objects;
import java.util.UUID;

public final class Member {
    private final MemberId id;
    private final UUID tenantId;
    private final UUID userId;
    private final MemberLevel level;
    private final int points;
    private final String status;

    private Member(MemberId id, UUID tenantId, UUID userId,
                   MemberLevel level, int points, String status) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.userId = Objects.requireNonNull(userId);
        this.level = Objects.requireNonNull(level);
        this.points = points;
        this.status = Objects.requireNonNull(status);
        if (points < 0) throw new IllegalArgumentException("points negative");
    }

    public static Member create(UUID tenantId, UUID userId) {
        return new Member(new MemberId(Ids.next()), tenantId, userId,
                          MemberLevel.BRONZE, 0, "ACTIVE");
    }

    public static Member reconstitute(MemberId id, UUID tenantId, UUID userId,
                                       MemberLevel level, int points, String status) {
        return new Member(id, tenantId, userId, level, points, status);
    }

    public Member awardPoints(int pts) {
        if (pts < 0) throw new IllegalArgumentException("pts must be >= 0");
        int newTotal = this.points + pts;
        return new Member(id, tenantId, userId, MemberLevel.forPoints(newTotal), newTotal, status);
    }

    // Accessors
    public MemberId id()        { return id; }
    public UUID tenantId()      { return tenantId; }
    public UUID userId()        { return userId; }
    public MemberLevel level()  { return level; }
    public int points()         { return points; }
    public String status()      { return status; }
}
```

- [ ] Run: `./gradlew :member:test --tests '...MemberTest'` — PASS.

- [ ] Commit: `git commit -m "✅ add Member aggregate with points system and level auto-upgrade"`

---

## Task 4 — Repository + auto-create listener (TDD)

- [ ] Create `MemberJooqRepository.java` — methods: `save(Member)`, `findById(MemberId)`, `findByUserId(UUID)`, `listByTenant(PageQuery)`.

- [ ] Write repository test (Testcontainers + `@DataJooqTest` pattern):
```java
@Test void shouldSaveAndFindByUserId() { ... }
@Test void shouldAutoIncrementPointsOnAward() { ... }
```

- [ ] Create `UserRegisteredMemberListener.java` — creates `Member` automatically after user registers:
```java
@Component
class UserRegisteredMemberListener {
    private final MemberJooqRepository memberRepo;
    UserRegisteredMemberListener(MemberJooqRepository memberRepo) {
        this.memberRepo = memberRepo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(UserRegistered event) {
        // UserRegistered carries userId and tenantId after Wave 1 updates
        Member member = Member.create(event.tenantId(), event.userId().value());
        memberRepo.save(member);
    }
}
```

Note: check the current `UserRegistered` event class. If it doesn't carry `tenantId`, update the event record in `user/domain/event/UserRegistered.java` to include `UUID tenantId` (and update `UserRegistered.of(...)` call sites).

- [ ] Write `UserRegisteredMemberListenerTest`:
```java
@Test void shouldCreateMemberOnUserRegistration() {
    UserRegistered event = UserRegistered.of(new UserId(Ids.next()), "u@e.com");
    // When tenantId is in the event:
    listener.on(event);
    verify(memberRepo).save(argThat(m -> m.points() == 0));
}
```

- [ ] Run: `./gradlew :member:test` — all green.

- [ ] Commit: `git commit -m "✅ add MemberJooqRepository and auto-create listener on UserRegistered"`

---

## Task 5 — MemberService + MemberController (TDD)

- [ ] Create `MemberService.java`:
```java
@Service
class MemberService {
    private final MemberJooqRepository repo;
    // ... constructor

    public Member getByUserId(UUID userId) {
        return repo.findByUserId(userId)
            .orElseThrow(() -> new AppException(AppCode.NOT_FOUND));
    }

    @Transactional
    public Member awardPoints(UUID userId, int points) {
        Member m = getByUserId(userId);
        Member updated = m.awardPoints(points);
        repo.save(updated);
        return updated;
    }

    public PageResult<Member> list(PageQuery query) {
        return repo.listByTenant(query);
    }
}
```

- [ ] Write `MemberServiceTest` (`@ExtendWith(MockitoExtension.class)`):
```java
@Test void shouldAwardPointsAndUpgradeLevel() { ... }
@Test void shouldThrowNotFoundForUnknownUser() { ... }
```

- [ ] Create `MemberController.java`:
```
GET  /api/v1/members/me               @SaCheckLogin — own member profile
GET  /api/v1/admin/members            @SaCheckRole("ADMIN") — list all members
POST /api/v1/admin/members/{userId}/points  @SaCheckRole("ADMIN") — award points
```

Request/Response records:
```java
record AwardPointsRequest(@Min(1) int points) {}
record MemberResponse(String id, String userId, String level, int points, String status) {
    static MemberResponse from(Member m) { ... }
}
```

- [ ] Run: `./gradlew :member:test` — all green.

- [ ] Run: `./gradlew check` — all modules green.

- [ ] Run code review: invoke `superpowers:requesting-code-review`. Fix all findings.

- [ ] Commit:
```bash
git add .
git commit -m "✨ implement Member module: loyalty system with points, levels, and auto-registration"
```

- [ ] Update OpenAPI snapshot: `./gradlew :app:updateOpenApiSnapshot` then commit.
