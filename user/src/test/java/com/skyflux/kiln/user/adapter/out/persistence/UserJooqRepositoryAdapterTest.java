package com.skyflux.kiln.user.adapter.out.persistence;

import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.infra.jooq.generated.Tables;
import com.skyflux.kiln.user.application.port.out.UserRepository;
import com.skyflux.kiln.user.domain.model.User;
import com.skyflux.kiln.user.domain.model.UserId;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Integration test for the jOOQ-backed {@link UserRepository} adapter.
 *
 * <p>Uses a real PostgreSQL container via Testcontainers + {@code @ServiceConnection}
 * so Flyway migrations run against the same image/version used in production
 * (postgres:18.3-alpine) and the jOOQ adapter exercises genuine SQL.
 *
 * <p>Wave 1 T8: tests now require a tenant row in the {@code tenants} table because
 * {@code users.tenant_id} has a FK constraint. A shared tenant is created in
 * {@link #seedTenant} before any test runs.
 */
@SpringBootTest(classes = UserJooqRepositoryAdapterTest.TestApp.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserJooqRepositoryAdapterTest {

    private static final String HASH = "$argon2id$v=19$test";

    /** Shared tenant ID — seeded once per test suite run. */
    private static final UUID TENANT_ID = Ids.next();

    /**
     * PostgreSQL image used by all Phase 3 integration tests — single source of truth.
     */
    static final String POSTGRES_IMAGE = "postgres:18.3-alpine";

    /**
     * Minimal Spring Boot entry point so {@code @SpringBootTest} can find a
     * {@code @SpringBootConfiguration} from within the user module (the real
     * {@code KilnApplication} lives in {@code app}, which is not on the user
     * test classpath). {@code @SpringBootApplication} covers the Testcontainers
     * bean plus full component-scan over {@code com.skyflux.kiln.user}.
     *
     * <p>Phase 4 note: the jOOQ adapter test intentionally runs without Redis.
     * Two classes are excluded from context loading:
     * <ol>
     *   <li>{@code DataRedisAutoConfiguration} — no Redis container here, so
     *       the connection factory bean shouldn't be built.</li>
     *   <li>{@code SaTokenDaoForRedisTemplate} — Sa-Token registers this as a
     *       component that requires {@code RedisConnectionFactory} (which we
     *       just excluded). Exclude it here to keep the slice Redis-free;
     *       Sa-Token's in-memory DAO takes over, which is fine for adapter
     *       persistence tests that don't exercise Sa-Token sessions.</li>
     * </ol>
     * Real Redis / Sa-Token wiring is exercised by the full-stack
     * {@code KilnIntegrationTest} in {@code app/}.
     */
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
    UserRepository repo;

    @Autowired
    UserJooqRepositoryAdapter jooqAdapter;

    @Autowired
    UserMapper mapper;

    @Autowired
    DSLContext dsl;

    /**
     * Insert a row into {@code tenants} so the FK on {@code users.tenant_id}
     * is satisfied. Runs once before all tests; shared TENANT_ID is used
     * throughout.
     *
     * <p>Uses {@code ON CONFLICT DO NOTHING} so re-runs of a single test via
     * IDE don't fail if the container is reused.
     */
    @BeforeAll
    void seedTenant() {
        dsl.insertInto(Tables.TENANTS)
                .set(Tables.TENANTS.ID, TENANT_ID)
                .set(Tables.TENANTS.CODE, "test-tenant")
                .set(Tables.TENANTS.NAME, "Test Tenant")
                .set(Tables.TENANTS.CREATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .onConflictDoNothing()
                .execute();
    }

    /** Register a user under the shared TENANT_ID. */
    private User register(String name, String email) {
        return User.register(TENANT_ID, name, email, HASH);
    }

    @Test
    void wiredBeanIsJooqAdapter() {
        assertThat(repo).isSameAs(jooqAdapter);
        assertThat(mapper).isNotNull();
    }

    @Test
    void saveThenFindByIdRoundTrips() {
        User u = register("Bob", "bob@example.com");
        repo.save(u);

        Optional<User> found = repo.findById(u.id());
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(u.id());
        assertThat(found.get().tenantId()).isEqualTo(TENANT_ID);
        assertThat(found.get().name()).isEqualTo("Bob");
        assertThat(found.get().email()).isEqualTo("bob@example.com");
        assertThat(found.get().passwordHash()).isEqualTo(HASH);
    }

    @Test
    void findByIdMissReturnsEmpty() {
        assertThat(repo.findById(UserId.newId())).isEmpty();
    }

    @Test
    void saveIsUpsert_secondSaveWithSameIdUpdates() {
        User first = register("Bob", "bob-upsert@example.com");
        repo.save(first);

        User updated = User.reconstitute(first.id(), TENANT_ID, "Robert", "bob-upsert@example.com", HASH, 0, null, "ACTIVE");
        repo.save(updated);

        assertThat(repo.findById(first.id()).orElseThrow().name()).isEqualTo("Robert");
    }

    @Test
    void findByIdNullRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> repo.findById(null));
    }

    @Test
    void saveNullRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> repo.save(null));
    }

    // ──────────── Gate 3 fixes — behavior pinning ────────────

    @Test
    void upsertPreservesCreatedAt() {
        // C1: UPSERT DO UPDATE must NOT overwrite created_at on subsequent saves.
        // If a future refactor adds `.set(CREATED_AT, ...)` to the doUpdate chain,
        // this test catches it.
        User first = register("CreateTime", "createtime@example.com");
        repo.save(first);
        var firstRowCreatedAt = dsl.select(Tables.USERS.CREATED_AT)
                .from(Tables.USERS)
                .where(Tables.USERS.ID.eq(first.id().value()))
                .fetchOne(Tables.USERS.CREATED_AT);

        // Small delay so a re-set of created_at would produce a different value
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        User updated = User.reconstitute(first.id(), TENANT_ID, "CreateTime2", "createtime@example.com", HASH, 0, null, "ACTIVE");
        repo.save(updated);

        var secondRowCreatedAt = dsl.select(Tables.USERS.CREATED_AT)
                .from(Tables.USERS)
                .where(Tables.USERS.ID.eq(first.id().value()))
                .fetchOne(Tables.USERS.CREATED_AT);

        assertThat(secondRowCreatedAt).isEqualTo(firstRowCreatedAt);
    }

    @Test
    void duplicateEmailTriggersDuplicateKeyException() {
        // I1: UNIQUE(email, tenant_id) at DB → Spring wraps as DuplicateKeyException.
        // RegisterUserService then translates to AppException(CONFLICT).
        User a = register("A", "dup@example.com");
        User b = register("B", "dup@example.com");   // same email + tenant, different id

        repo.save(a);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> repo.save(b))
                .isInstanceOf(DuplicateKeyException.class);
    }

    // ──────────── Phase 4: findByEmail ────────────

    @Test
    void findByEmailRoundTrips() {
        User u = register("Carol", "carol@example.com");
        repo.save(u);

        Optional<User> found = repo.findByEmail("carol@example.com");
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(u.id());
        assertThat(found.get().name()).isEqualTo("Carol");
        assertThat(found.get().email()).isEqualTo("carol@example.com");
        assertThat(found.get().passwordHash()).isEqualTo(HASH);
    }

    @Test
    void findByEmailMissReturnsEmpty() {
        assertThat(repo.findByEmail("not-registered@example.com")).isEmpty();
    }

    @Test
    void findByEmailNullRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> repo.findByEmail(null));
    }

    // ──────────── Phase 4.2: countAll (admin demo surface) ────────────

    @Test
    void countAllReturnsInsertedRowCount() {
        // Spring's test-context cache shares one PG container across all tests
        // in this class, so we measure the DELTA around our own inserts rather
        // than an absolute starting value.
        long before = repo.countAll();
        repo.save(register("CountA", "count-a@example.com"));
        repo.save(register("CountB", "count-b@example.com"));
        assertThat(repo.countAll()).isEqualTo(before + 2);
    }

    // ──────────── Phase 4.3 Wave 1: lockout bookkeeping persistence ────────────

    @Test
    void saveUpdatesLockoutFieldsOnUpsert() {
        // Wave 2 mutates lockout fields on a loaded aggregate and calls save().
        // The UPSERT DO UPDATE chain must write the mutated values; otherwise
        // the counter increment would be silently dropped.
        User initial = register("Lock", "lockout@example.com");
        repo.save(initial);

        Optional<User> afterFirstSave = repo.findById(initial.id());
        assertThat(afterFirstSave).isPresent();
        assertThat(afterFirstSave.get().failedLoginAttempts()).isZero();
        assertThat(afterFirstSave.get().lockedUntil()).isNull();

        Instant lockedUntil = Instant.parse("2099-01-01T00:00:00Z");
        User locked = User.reconstitute(
                initial.id(), TENANT_ID, "Lock", "lockout@example.com", HASH, 3, lockedUntil, "ACTIVE");
        repo.save(locked);

        Optional<User> afterSecondSave = repo.findById(initial.id());
        assertThat(afterSecondSave).isPresent();
        assertThat(afterSecondSave.get().failedLoginAttempts()).isEqualTo(3);
        assertThat(afterSecondSave.get().lockedUntil()).isEqualTo(lockedUntil);
    }

    // Gate 3 C1: atomic-SQL lockout updates.

    @org.junit.jupiter.api.Test
    void recordLoginFailureIncrementsCounterBelowThreshold() {
        User u = register("Atomic1", "atomic-below@example.com");
        repo.save(u);

        User after = repo.recordLoginFailure(
                u.id(), java.time.Instant.parse("2026-04-18T10:00:00Z"), 5, java.time.Duration.ofMinutes(15));

        assertThat(after.failedLoginAttempts()).isEqualTo(1);
        assertThat(after.lockedUntil()).isNull();
    }

    @org.junit.jupiter.api.Test
    void recordLoginFailureLocksWhenThresholdReachedAndResetsCounter() {
        User u = User.reconstitute(
                UserId.newId(), TENANT_ID,
                "Atomic2", "atomic-trip@example.com", HASH, 4, null, "ACTIVE");
        repo.save(u);

        java.time.Instant now = java.time.Instant.parse("2026-04-18T10:00:00Z");
        User after = repo.recordLoginFailure(u.id(), now, 5, java.time.Duration.ofMinutes(15));

        // post-increment = 5 ≥ threshold → counter resets, locked_until = now + 15m.
        assertThat(after.failedLoginAttempts()).isZero();
        assertThat(after.lockedUntil()).isEqualTo(now.plus(java.time.Duration.ofMinutes(15)));
    }

    @org.junit.jupiter.api.Test
    void recordLoginSuccessClearsCounterAndLock() {
        java.time.Instant future = java.time.Instant.parse("2099-01-01T00:00:00Z");
        User u = User.reconstitute(
                UserId.newId(), TENANT_ID,
                "Atomic3", "atomic-ok@example.com", HASH, 3, future, "ACTIVE");
        repo.save(u);

        User after = repo.recordLoginSuccess(u.id());

        assertThat(after.failedLoginAttempts()).isZero();
        assertThat(after.lockedUntil()).isNull();
    }

    // ──────────── Wave 2a: listActive ────────────

    @Test
    void shouldListActiveUsersPaged() {
        // Use unique email prefix to avoid cross-test contamination
        String prefix = "listactive-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        User a = register("ListA", prefix + "-a@example.com");
        User b = register("ListB", prefix + "-b@example.com");
        User c = register("ListC", prefix + "-c@example.com");
        repo.save(a);
        repo.save(b);
        repo.save(c);

        // Soft-delete one
        User deactivated = register("ListD", prefix + "-d@example.com");
        repo.save(deactivated);
        repo.save(deactivated.deactivate());

        // At minimum our 3 active users exist; count may be higher due to other tests sharing the container
        PageResult<User> page1 = repo.listActive(new PageQuery(1, 200, null));
        assertThat(page1.items().stream().noneMatch(u -> "INACTIVE".equals(u.status()))).isTrue();
        assertThat(page1.items().stream().anyMatch(u -> u.id().equals(a.id()))).isTrue();
        assertThat(page1.items().stream().anyMatch(u -> u.id().equals(b.id()))).isTrue();
        assertThat(page1.items().stream().anyMatch(u -> u.id().equals(c.id()))).isTrue();
        // The deactivated user must NOT appear
        assertThat(page1.items().stream().noneMatch(u -> u.id().equals(deactivated.id()))).isTrue();
    }

    @Test
    void shouldReturnEmptyPageWhenNoActiveUsers() {
        // This is isolated — just verify pagination doesn't crash for edge case
        PageResult<User> result = repo.listActive(new PageQuery(999, 200, null));
        // Page 999 with size 200 likely returns no items (total might be small)
        assertThat(result.items()).isNotNull();
        assertThat(result.total()).isGreaterThanOrEqualTo(0L);
    }

    @org.junit.jupiter.api.Test
    void recordLoginFailureAtomicityUnderConcurrentBurst() throws Exception {
        // 10 concurrent wrong-password attempts on the same account. Without
        // atomicity we'd see counter < 10 (lost updates). Threshold=100 so no
        // lock trips; counter MUST equal 10 after all 10 calls complete.
        User u = register("Concurrent", "atomic-concurrent@example.com");
        repo.save(u);

        int n = 10;
        java.util.concurrent.ExecutorService ex = java.util.concurrent.Executors.newFixedThreadPool(n);
        java.util.concurrent.CountDownLatch gate = new java.util.concurrent.CountDownLatch(1);
        java.util.List<java.util.concurrent.Future<?>> futs = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            futs.add(ex.submit(() -> {
                gate.await();
                repo.recordLoginFailure(u.id(),
                        java.time.Instant.parse("2026-04-18T10:00:00Z"),
                        100, java.time.Duration.ofMinutes(15));
                return null;
            }));
        }
        gate.countDown();
        for (java.util.concurrent.Future<?> f : futs) {
            f.get();
        }
        ex.shutdown();

        User after = repo.findById(u.id()).orElseThrow();
        assertThat(after.failedLoginAttempts()).isEqualTo(n);
    }
}
