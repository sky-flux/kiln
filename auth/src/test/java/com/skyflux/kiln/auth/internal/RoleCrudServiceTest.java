package com.skyflux.kiln.auth.internal;

import com.skyflux.kiln.auth.domain.Role;
import com.skyflux.kiln.auth.repo.RoleJooqRepository;
import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.common.util.Ids;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RoleCrudService}.
 *
 * <p>Mocks {@link RoleJooqRepository} — no Spring context, no DB.
 */
@ExtendWith(MockitoExtension.class)
class RoleCrudServiceTest {

    @Mock
    RoleJooqRepository roleRepo;

    RoleCrudService service;

    @BeforeEach
    void setUp() {
        service = new RoleCrudService(roleRepo);
    }

    @Test
    void shouldCreateRole() {
        UUID tenantId = Ids.next();
        when(roleRepo.findByCode("EDITOR")).thenReturn(Optional.empty());

        Role created = service.createRole(tenantId, "EDITOR", "Editor");

        assertThat(created.code()).isEqualTo("EDITOR");
        assertThat(created.name()).isEqualTo("Editor");
        assertThat(created.tenantId()).isEqualTo(tenantId);
        assertThat(created.id()).isNotNull();
        verify(roleRepo).save(any());
    }

    @Test
    void shouldRejectDuplicateCode() {
        UUID tenantId = Ids.next();
        when(roleRepo.findByCode("EDITOR")).thenReturn(
                Optional.of(new Role(Ids.next(), "EDITOR", "X", tenantId)));

        assertThatThrownBy(() -> service.createRole(tenantId, "EDITOR", "Dup"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).appCode())
                .isEqualTo(AppCode.CONFLICT);

        verify(roleRepo, never()).save(any());
    }

    @Test
    void shouldDeleteRole() {
        UUID roleId = Ids.next();

        service.deleteRole(roleId);

        verify(roleRepo).delete(roleId);
    }

    @Test
    void shouldListRoles() {
        UUID tenantId = Ids.next();
        List<Role> roles = List.of(
                new Role(Ids.next(), "ADMIN", "Administrator", tenantId),
                new Role(Ids.next(), "USER", "Regular user", tenantId));
        when(roleRepo.listAll()).thenReturn(roles);

        List<Role> result = service.listRoles();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Role::code).containsExactly("ADMIN", "USER");
    }
}
