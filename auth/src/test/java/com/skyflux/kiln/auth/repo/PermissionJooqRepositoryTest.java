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
 * Integration test for {@link PermissionJooqRepository}.
 *
 * <p>Verifies the transitive lookup: user → user_roles → role_permissions →
 * permissions. Uses the V4 seed wiring:
 * <ul>
 *     <li>ADMIN grants {@code user.admin} + {@code user.read}</li>
 *     <li>USER  grants {@code user.read}</li>
 * </ul>
 */
@SpringBootTest(classes = PermissionJooqRepositoryTest.TestApp.class)
class PermissionJooqRepositoryTest {

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
    PermissionJooqRepository repo;

    @Autowired
    UserRoleJooqRepository userRoleRepo;

    @Autowired
    DSLContext dsl;

    @Test
    void adminRoleGrantsAllPermissions() {
        UUID userId = seedUser("admin-perms@example.com");
        userRoleRepo.assign(userId, ADMIN_ROLE_ID);

        List<String> codes = repo.findCodesByUserId(userId);

        assertThat(codes).containsExactlyInAnyOrder("user.admin", "user.read");
    }

    @Test
    void userRoleGrantsOnlyReadPermission() {
        UUID userId = seedUser("user-perms@example.com");
        userRoleRepo.assign(userId, USER_ROLE_ID);

        List<String> codes = repo.findCodesByUserId(userId);

        assertThat(codes).containsExactly("user.read");
    }

    @Test
    void userWithBothRolesGetsDeduplicatedPermissions() {
        UUID userId = seedUser("both-roles@example.com");
        userRoleRepo.assign(userId, ADMIN_ROLE_ID);
        userRoleRepo.assign(userId, USER_ROLE_ID);

        List<String> codes = repo.findCodesByUserId(userId);

        // user.read is granted by BOTH roles — must appear once, not twice.
        assertThat(codes).containsExactlyInAnyOrder("user.admin", "user.read");
    }

    @Test
    void userWithNoRolesGetsNoPermissions() {
        UUID userId = seedUser("no-roles@example.com");

        assertThat(repo.findCodesByUserId(userId)).isEmpty();
    }

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
