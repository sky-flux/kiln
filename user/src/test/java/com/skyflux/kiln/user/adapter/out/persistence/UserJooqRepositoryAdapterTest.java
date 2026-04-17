package com.skyflux.kiln.user.adapter.out.persistence;

import com.skyflux.kiln.infra.jooq.generated.Tables;
import com.skyflux.kiln.user.application.port.out.UserRepository;
import com.skyflux.kiln.user.domain.model.User;
import com.skyflux.kiln.user.domain.model.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DuplicateKeyException;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Integration test for the jOOQ-backed {@link UserRepository} adapter.
 *
 * <p>Uses a real PostgreSQL container via Testcontainers + {@code @ServiceConnection}
 * so Flyway migrations run against the same image/version used in production
 * (postgres:18.3-alpine) and the jOOQ adapter exercises genuine SQL.
 */
@SpringBootTest(classes = UserJooqRepositoryAdapterTest.TestApp.class)
class UserJooqRepositoryAdapterTest {

    private static final String HASH = "$argon2id$v=19$test";

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

    @Test
    void wiredBeanIsJooqAdapter() {
        assertThat(repo).isSameAs(jooqAdapter);
        assertThat(mapper).isNotNull();
    }

    @Test
    void saveThenFindByIdRoundTrips() {
        User u = User.register("Bob", "bob@example.com", HASH);
        repo.save(u);

        Optional<User> found = repo.findById(u.id());
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(u.id());
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
        User first = User.register("Bob", "bob-upsert@example.com", HASH);
        repo.save(first);

        User updated = User.reconstitute(first.id(), "Robert", "bob-upsert@example.com", HASH, 0, null);
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
        User first = User.register("CreateTime", "createtime@example.com", HASH);
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

        User updated = User.reconstitute(first.id(), "CreateTime2", "createtime@example.com", HASH, 0, null);
        repo.save(updated);

        var secondRowCreatedAt = dsl.select(Tables.USERS.CREATED_AT)
                .from(Tables.USERS)
                .where(Tables.USERS.ID.eq(first.id().value()))
                .fetchOne(Tables.USERS.CREATED_AT);

        assertThat(secondRowCreatedAt).isEqualTo(firstRowCreatedAt);
    }

    @Test
    void duplicateEmailTriggersDuplicateKeyException() {
        // I1: UNIQUE(email) at DB → Spring wraps as DuplicateKeyException.
        // RegisterUserService then translates to AppException(CONFLICT).
        User a = User.register("A", "dup@example.com", HASH);
        User b = User.register("B", "dup@example.com", HASH);   // same email, different id

        repo.save(a);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> repo.save(b))
                .isInstanceOf(DuplicateKeyException.class);
    }

    // ──────────── Phase 4: findByEmail ────────────

    @Test
    void findByEmailRoundTrips() {
        User u = User.register("Carol", "carol@example.com", HASH);
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
        repo.save(User.register("CountA", "count-a@example.com", HASH));
        repo.save(User.register("CountB", "count-b@example.com", HASH));
        assertThat(repo.countAll()).isEqualTo(before + 2);
    }

    // ──────────── Phase 4.3 Wave 1: lockout bookkeeping persistence ────────────

    @Test
    void saveUpdatesLockoutFieldsOnUpsert() {
        // Wave 2 mutates lockout fields on a loaded aggregate and calls save().
        // The UPSERT DO UPDATE chain must write the mutated values; otherwise
        // the counter increment would be silently dropped.
        User initial = User.register("Lock", "lockout@example.com", HASH);
        repo.save(initial);

        Optional<User> afterFirstSave = repo.findById(initial.id());
        assertThat(afterFirstSave).isPresent();
        assertThat(afterFirstSave.get().failedLoginAttempts()).isZero();
        assertThat(afterFirstSave.get().lockedUntil()).isNull();

        Instant lockedUntil = Instant.parse("2099-01-01T00:00:00Z");
        User locked = User.reconstitute(
                initial.id(), "Lock", "lockout@example.com", HASH, 3, lockedUntil);
        repo.save(locked);

        Optional<User> afterSecondSave = repo.findById(initial.id());
        assertThat(afterSecondSave).isPresent();
        assertThat(afterSecondSave.get().failedLoginAttempts()).isEqualTo(3);
        assertThat(afterSecondSave.get().lockedUntil()).isEqualTo(lockedUntil);
    }

    // Gate 3 C1: atomic-SQL lockout updates.

    @org.junit.jupiter.api.Test
    void recordLoginFailureIncrementsCounterBelowThreshold() {
        User u = User.register("Atomic1", "atomic-below@example.com", HASH);
        repo.save(u);

        User after = repo.recordLoginFailure(
                u.id(), java.time.Instant.parse("2026-04-18T10:00:00Z"), 5, java.time.Duration.ofMinutes(15));

        assertThat(after.failedLoginAttempts()).isEqualTo(1);
        assertThat(after.lockedUntil()).isNull();
    }

    @org.junit.jupiter.api.Test
    void recordLoginFailureLocksWhenThresholdReachedAndResetsCounter() {
        User u = User.reconstitute(
                com.skyflux.kiln.user.domain.model.UserId.newId(),
                "Atomic2", "atomic-trip@example.com", HASH, 4, null);
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
                com.skyflux.kiln.user.domain.model.UserId.newId(),
                "Atomic3", "atomic-ok@example.com", HASH, 3, future);
        repo.save(u);

        User after = repo.recordLoginSuccess(u.id());

        assertThat(after.failedLoginAttempts()).isZero();
        assertThat(after.lockedUntil()).isNull();
    }

    @org.junit.jupiter.api.Test
    void recordLoginFailureAtomicityUnderConcurrentBurst() throws Exception {
        // 10 concurrent wrong-password attempts on the same account. Without
        // atomicity we'd see counter < 10 (lost updates). Threshold=100 so no
        // lock trips; counter MUST equal 10 after all 10 calls complete.
        User u = User.register("Concurrent", "atomic-concurrent@example.com", HASH);
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

    @org.springframework.beans.factory.annotation.Autowired
    org.jooq.DSLContext dsl;
}
