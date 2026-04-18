package com.skyflux.kiln.auth.repo;

import com.skyflux.kiln.auth.domain.Role;
import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.tenant.api.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test — exercises {@link RoleJooqRepository} methods against a real
 * PostgreSQL container. All tests wrap DB access in a {@code ScopedValue.where}
 * binding for {@link TenantContext#CURRENT} because V9 enables RLS on the
 * {@code roles} table; without a bound tenant_id the session variable
 * {@code app.tenant_id} is unset and RLS rejects every query.
 */
@SpringBootTest(classes = RoleJooqRepositoryTest.TestApp.class)
class RoleJooqRepositoryTest {

    static final UUID SYSTEM_TENANT_ID = UUID.fromString("00000000-0000-7000-8000-000000000001");

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
    void findByCodeReturnsSeededAdmin() throws Exception {
        Optional<Role> found = ScopedValue.where(TenantContext.CURRENT, SYSTEM_TENANT_ID)
                .call(() -> repo.findByCode("ADMIN"));

        assertThat(found).isPresent();
        assertThat(found.get().code()).isEqualTo("ADMIN");
        assertThat(found.get().name()).isEqualTo("Administrator");
        assertThat(found.get().tenantId()).isEqualTo(SYSTEM_TENANT_ID);
    }

    @Test
    void findByCodeReturnsSeededUser() throws Exception {
        Optional<Role> found = ScopedValue.where(TenantContext.CURRENT, SYSTEM_TENANT_ID)
                .call(() -> repo.findByCode("USER"));

        assertThat(found).isPresent();
        assertThat(found.get().code()).isEqualTo("USER");
    }

    @Test
    void findByCodeUnknownReturnsEmpty() throws Exception {
        Optional<Role> found = ScopedValue.where(TenantContext.CURRENT, SYSTEM_TENANT_ID)
                .call(() -> repo.findByCode("NOT-A-ROLE"));

        assertThat(found).isEmpty();
    }

    @Test
    void findByIdReturnsSeededAdmin() throws Exception {
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        Optional<Role> found = ScopedValue.where(TenantContext.CURRENT, SYSTEM_TENANT_ID)
                .call(() -> repo.findById(adminId));

        assertThat(found).isPresent();
        assertThat(found.get().code()).isEqualTo("ADMIN");
    }

    @Test
    void findByIdUnknownReturnsEmpty() throws Exception {
        Optional<Role> found = ScopedValue.where(TenantContext.CURRENT, SYSTEM_TENANT_ID)
                .call(() -> repo.findById(UUID.randomUUID()));

        assertThat(found).isEmpty();
    }

    @Test
    void shouldSaveAndFindNewRole() throws Exception {
        UUID id = Ids.next();
        Role role = new Role(id, "EDITOR", "Editor", SYSTEM_TENANT_ID);

        ScopedValue.where(TenantContext.CURRENT, SYSTEM_TENANT_ID).run(() -> repo.save(role));

        Optional<Role> found = ScopedValue.where(TenantContext.CURRENT, SYSTEM_TENANT_ID)
                .call(() -> repo.findById(id));
        assertThat(found).isPresent();
        assertThat(found.get().code()).isEqualTo("EDITOR");
    }

    @Test
    void shouldDeleteRole() throws Exception {
        UUID id = Ids.next();
        Role role = new Role(id, "DELETABLE", "Deletable", SYSTEM_TENANT_ID);

        ScopedValue.where(TenantContext.CURRENT, SYSTEM_TENANT_ID).run(() -> repo.save(role));
        ScopedValue.where(TenantContext.CURRENT, SYSTEM_TENANT_ID).run(() -> repo.delete(id));

        Optional<Role> found = ScopedValue.where(TenantContext.CURRENT, SYSTEM_TENANT_ID)
                .call(() -> repo.findById(id));
        assertThat(found).isEmpty();
    }

    @Test
    void shouldListAllRolesForCurrentTenant() throws Exception {
        UUID id = Ids.next();
        Role role = new Role(id, "LISTER", "Lister", SYSTEM_TENANT_ID);

        ScopedValue.where(TenantContext.CURRENT, SYSTEM_TENANT_ID).run(() -> repo.save(role));

        List<Role> roles = ScopedValue.where(TenantContext.CURRENT, SYSTEM_TENANT_ID)
                .call(() -> repo.listAll());
        assertThat(roles).extracting(Role::code).contains("LISTER", "ADMIN", "USER");
    }
}
