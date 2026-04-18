package com.skyflux.kiln.tenant.repo;

import com.skyflux.kiln.tenant.api.TenantId;
import com.skyflux.kiln.tenant.domain.Tenant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TenantJooqRepositoryTest.TestApp.class)
class TenantJooqRepositoryTest {

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

    @Autowired TenantJooqRepository repo;

    @Test void shouldSaveAndFindById() {
        TenantId id = TenantId.newId();
        repo.save(new Tenant(id, "acme", "ACME Corp", "ACTIVE", null));
        assertThat(repo.findById(id)).isPresent()
            .get().extracting(Tenant::code).isEqualTo("acme");
    }

    @Test void shouldFindByCode() {
        TenantId id = TenantId.newId();
        repo.save(new Tenant(id, "beta-co", "Beta Co", "ACTIVE", null));
        assertThat(repo.findByCode("beta-co")).isPresent();
    }

    @Test void shouldReturnEmptyForUnknownCode() {
        assertThat(repo.findByCode("no-such-tenant")).isEmpty();
    }

    @Test void shouldFindSystemTenantSeededByMigration() {
        assertThat(repo.findByCode("system")).isPresent();
    }
}
