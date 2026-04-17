package com.skyflux.kiln.auth.repo;

import com.skyflux.kiln.infra.jooq.generated.Tables;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link UserRoleJooqRepository}.
 *
 * <p>Exercises the INSERT ... ON CONFLICT DO NOTHING idempotency and the
 * cross-table JOIN that answers {@code findRoleCodesByUserId}. Both paths
 * require real PostgreSQL — {@code ON CONFLICT} isn't portable to H2 used
 * by DDLDatabase-mode codegen.
 *
 * <p>Seeds a throwaway {@code users} row first; {@code user_roles.user_id}
 * has an FK on {@code users.id} so inserting without a matching row violates
 * the constraint. {@code password_hash} is NOT NULL so we pass an obviously
 * fake token.
 */
@SpringBootTest(classes = UserRoleJooqRepositoryTest.TestApp.class)
class UserRoleJooqRepositoryTest {

    private static final UUID ADMIN_ROLE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ROLE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    @SpringBootApplication(exclude = {
            org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration.class,
            cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate.class
    })
    static class TestApp {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:18.3-alpine");
        }
    }

    @Autowired
    UserRoleJooqRepository repo;

    @Autowired
    DSLContext dsl;

    @Test
    void assignThenFindRoundTrips() {
        UUID userId = seedUser("find-roundtrip@example.com");

        repo.assign(userId, ADMIN_ROLE_ID);

        List<String> codes = repo.findRoleCodesByUserId(userId);
        assertThat(codes).containsExactly("ADMIN");
    }

    @Test
    void assignMultipleRolesToSameUser() {
        UUID userId = seedUser("multi-role@example.com");

        repo.assign(userId, ADMIN_ROLE_ID);
        repo.assign(userId, USER_ROLE_ID);

        List<String> codes = repo.findRoleCodesByUserId(userId);
        assertThat(codes).containsExactlyInAnyOrder("ADMIN", "USER");
    }

    @Test
    void assignIsIdempotent_noDuplicateRow_noException() {
        UUID userId = seedUser("idempotent@example.com");

        repo.assign(userId, USER_ROLE_ID);
        repo.assign(userId, USER_ROLE_ID);   // second call must not throw

        // Only a single row in user_roles for (userId, USER_ROLE_ID)
        int rowCount = dsl.fetchCount(
                Tables.USER_ROLES,
                Tables.USER_ROLES.USER_ID.eq(userId)
                        .and(Tables.USER_ROLES.ROLE_ID.eq(USER_ROLE_ID)));
        assertThat(rowCount).isEqualTo(1);
    }

    @Test
    void findRoleCodesByUnknownUserReturnsEmpty() {
        assertThat(repo.findRoleCodesByUserId(UUID.randomUUID())).isEmpty();
    }

    /**
     * Inserts a minimal {@code users} row so we can satisfy the FK on
     * {@code user_roles.user_id}. Each test uses its own email to avoid
     * UNIQUE(email) collisions across test methods.
     */
    private UUID seedUser(String email) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        dsl.insertInto(Tables.USERS)
                .set(Tables.USERS.ID, id)
                .set(Tables.USERS.NAME, "test-" + email)
                .set(Tables.USERS.EMAIL, email)
                .set(Tables.USERS.PASSWORD_HASH, "$argon2id$test-fixture")
                .set(Tables.USERS.CREATED_AT, now)
                .set(Tables.USERS.UPDATED_AT, now)
                .execute();
        return id;
    }
}
