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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Integration test for the jOOQ-backed {@link UserRepository} adapter.
 *
 * <p>Uses a real PostgreSQL container via Testcontainers + {@code @ServiceConnection}
 * so Flyway migrations run against the same image/version used in production
 * (postgres:18-alpine) and the jOOQ adapter exercises genuine SQL.
 */
@SpringBootTest(classes = UserJooqRepositoryAdapterTest.TestApp.class)
class UserJooqRepositoryAdapterTest {

    /** PostgreSQL image used by all Phase 3 integration tests — single source of truth. */
    static final String POSTGRES_IMAGE = "postgres:18-alpine";

    /**
     * Minimal Spring Boot entry point so {@code @SpringBootTest} can find a
     * {@code @SpringBootConfiguration} from within the user module (the real
     * {@code KilnApplication} lives in {@code app}, which is not on the user
     * test classpath). {@code @SpringBootApplication} covers the Testcontainers
     * bean plus full component-scan over {@code com.skyflux.kiln.user}.
     */
    @SpringBootApplication
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
        User u = User.register("Bob", "bob@example.com");
        repo.save(u);

        Optional<User> found = repo.findById(u.id());
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(u.id());
        assertThat(found.get().name()).isEqualTo("Bob");
        assertThat(found.get().email()).isEqualTo("bob@example.com");
    }

    @Test
    void findByIdMissReturnsEmpty() {
        assertThat(repo.findById(UserId.newId())).isEmpty();
    }

    @Test
    void saveIsUpsert_secondSaveWithSameIdUpdates() {
        User first = User.register("Bob", "bob-upsert@example.com");
        repo.save(first);

        User updated = User.reconstitute(first.id(), "Robert", "bob-upsert@example.com");
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
        User first = User.register("CreateTime", "createtime@example.com");
        repo.save(first);
        var firstRowCreatedAt = dsl.select(Tables.USERS.CREATED_AT)
                .from(Tables.USERS)
                .where(Tables.USERS.ID.eq(first.id().value()))
                .fetchOne(Tables.USERS.CREATED_AT);

        // Small delay so a re-set of created_at would produce a different value
        try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        User updated = User.reconstitute(first.id(), "CreateTime2", "createtime@example.com");
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
        User a = User.register("A", "dup@example.com");
        User b = User.register("B", "dup@example.com");   // same email, different id

        repo.save(a);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> repo.save(b))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @org.springframework.beans.factory.annotation.Autowired
    org.jooq.DSLContext dsl;
}
