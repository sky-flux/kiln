package com.skyflux.kiln.tenant.internal;

import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.tenant.api.TenantId;
import com.skyflux.kiln.tenant.domain.Tenant;
import com.skyflux.kiln.tenant.repo.TenantJooqRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock TenantJooqRepository repo;
    @InjectMocks TenantService service;

    @Test void shouldCreateTenant() {
        when(repo.findByCode("acme")).thenReturn(Optional.empty());
        Tenant result = service.create("acme", "ACME Corp");
        assertThat(result.code()).isEqualTo("acme");
        assertThat(result.status()).isEqualTo("ACTIVE");
        verify(repo).save(any());
    }

    @Test void shouldRejectDuplicateCode() {
        when(repo.findByCode("acme")).thenReturn(Optional.of(
            new Tenant(TenantId.newId(), "acme", "Existing", "ACTIVE", null)));
        assertThatThrownBy(() -> service.create("acme", "Duplicate"))
            .isInstanceOf(AppException.class)
            .extracting(e -> ((AppException)e).appCode())
            .isEqualTo(AppCode.CONFLICT);
    }

    @Test void shouldTranslateDuplicateKeyExceptionFromSaveToConflict() {
        // Concurrent creation race: both threads pass the findByCode check, then one
        // fails at the DB UNIQUE(code) constraint. DuplicateKeyException from save()
        // must be translated to AppCode.CONFLICT, not bubble as a 500.
        when(repo.findByCode("race")).thenReturn(Optional.empty());
        doThrow(new DuplicateKeyException("dup code")).when(repo).save(any());
        assertThatThrownBy(() -> service.create("race", "Race Loser"))
            .isInstanceOf(AppException.class)
            .extracting(e -> ((AppException)e).appCode())
            .isEqualTo(AppCode.CONFLICT);
    }

    @Test void shouldSuspendTenant() {
        TenantId id = TenantId.newId();
        when(repo.findById(id)).thenReturn(Optional.of(
            new Tenant(id, "acme", "ACME", "ACTIVE", null)));
        service.suspend(id);
        verify(repo).save(argThat(t -> "SUSPENDED".equals(t.status())));
    }

    @Test void shouldThrowNotFoundWhenSuspendingUnknownTenant() {
        TenantId id = TenantId.newId();
        when(repo.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.suspend(id))
            .isInstanceOf(AppException.class)
            .extracting(e -> ((AppException)e).appCode())
            .isEqualTo(AppCode.NOT_FOUND);
    }

    @Test void shouldUpdateTenant() {
        TenantId id = TenantId.newId();
        when(repo.findById(id)).thenReturn(Optional.of(
            new Tenant(id, "acme", "ACME Corp", "ACTIVE", null)));
        Tenant updated = service.update(id, "ACME Updated", "ACTIVE");
        assertThat(updated.name()).isEqualTo("ACME Updated");
        verify(repo).save(argThat(t -> "ACME Updated".equals(t.name())));
    }

    @Test void shouldThrowNotFoundWhenUpdatingUnknownTenant() {
        TenantId id = TenantId.newId();
        when(repo.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(id, "Name", "ACTIVE"))
            .isInstanceOf(AppException.class)
            .extracting(e -> ((AppException)e).appCode())
            .isEqualTo(AppCode.NOT_FOUND);
    }

    @Test void shouldGetTenant() {
        TenantId id = TenantId.newId();
        Tenant tenant = new Tenant(id, "acme", "ACME", "ACTIVE", null);
        when(repo.findById(id)).thenReturn(Optional.of(tenant));
        Tenant result = service.get(id);
        assertThat(result.code()).isEqualTo("acme");
    }

    @Test void shouldThrowNotFoundWhenGettingUnknownTenant() {
        TenantId id = TenantId.newId();
        when(repo.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(id))
            .isInstanceOf(AppException.class)
            .extracting(e -> ((AppException)e).appCode())
            .isEqualTo(AppCode.NOT_FOUND);
    }
}
