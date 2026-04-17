package com.skyflux.kiln.auth.repo;

import com.skyflux.kiln.auth.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test — exercises the V4 {@code roles} seed rows against a real
 * PostgreSQL container. Mirrors the shape of
 * {@code UserJooqRepositoryAdapterTest} so Phase 4.2 keeps the same
 * "no Redis" envelope that Phase 3-4 established for persistence slices.
 */
@SpringBootTest(classes = RoleJooqRepositoryTest.TestApp.class)
class RoleJooqRepositoryTest {

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
    RoleJooqRepository repo;

    @Test
    void findByCodeReturnsSeededAdmin() {
        Optional<Role> found = repo.findByCode("ADMIN");

        assertThat(found).isPresent();
        assertThat(found.get().code()).isEqualTo("ADMIN");
        assertThat(found.get().name()).isEqualTo("Administrator");
    }

    @Test
    void findByCodeReturnsSeededUser() {
        Optional<Role> found = repo.findByCode("USER");

        assertThat(found).isPresent();
        assertThat(found.get().code()).isEqualTo("USER");
    }

    @Test
    void findByCodeUnknownReturnsEmpty() {
        assertThat(repo.findByCode("NOT-A-ROLE")).isEmpty();
    }

    @Test
    void findByIdReturnsSeededAdmin() {
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        Optional<Role> found = repo.findById(adminId);

        assertThat(found).isPresent();
        assertThat(found.get().code()).isEqualTo("ADMIN");
    }

    @Test
    void findByIdUnknownReturnsEmpty() {
        assertThat(repo.findById(UUID.randomUUID())).isEmpty();
    }
}
