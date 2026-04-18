package com.skyflux.kiln.tenant.config;

import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.tenant.api.TenantContext;
import com.skyflux.kiln.tenant.api.TenantId;
import com.skyflux.kiln.tenant.domain.Tenant;
import com.skyflux.kiln.tenant.repo.TenantJooqRepository;
import jakarta.servlet.FilterChain;
import org.jooq.ExecuteContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.sql.Connection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantFilterTest {

    @Mock TenantJooqRepository tenantRepo;
    @InjectMocks TenantFilter filter;

    @Test void shouldBindTenantContextFromHeader() throws Exception {
        TenantId tenantId = TenantId.newId();
        when(tenantRepo.findByCode("acme"))
            .thenReturn(Optional.of(new Tenant(tenantId, "acme", "ACME", "ACTIVE", null)));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Tenant-Code", "acme");
        AtomicReference<UUID> captured = new AtomicReference<>();
        FilterChain chain = (r, s) -> captured.set(TenantContext.CURRENT.get());

        filter.doFilterInternal(req, new MockHttpServletResponse(), chain);

        assertThat(captured.get()).isEqualTo(tenantId.value());
    }

    @Test void shouldPassThroughWithoutHeaderOnPublicRoute() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
        verify(tenantRepo, never()).findByCode(any());
    }

    @Test void shouldThrowNotFoundForUnknownTenantCode() {
        when(tenantRepo.findByCode("ghost")).thenReturn(Optional.empty());
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Tenant-Code", "ghost");

        assertThatThrownBy(() -> filter.doFilterInternal(req, new MockHttpServletResponse(), mock(FilterChain.class)))
            .isInstanceOf(AppException.class);
    }

    @Test void shouldPropagateIOExceptionFromChain() throws Exception {
        TenantId tenantId = TenantId.newId();
        when(tenantRepo.findByCode("acme"))
            .thenReturn(Optional.of(new Tenant(tenantId, "acme", "ACME", "ACTIVE", null)));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Tenant-Code", "acme");
        FilterChain chain = (r, s) -> { throw new java.io.IOException("simulated"); };

        assertThatThrownBy(() -> filter.doFilterInternal(req, new MockHttpServletResponse(), chain))
            .isInstanceOf(java.io.IOException.class)
            .hasMessageContaining("simulated");
    }

    @Test void shouldPropagateServletExceptionFromChain() throws Exception {
        TenantId tenantId = TenantId.newId();
        when(tenantRepo.findByCode("acme"))
            .thenReturn(Optional.of(new Tenant(tenantId, "acme", "ACME", "ACTIVE", null)));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Tenant-Code", "acme");
        FilterChain chain = (r, s) -> { throw new jakarta.servlet.ServletException("simulated"); };

        assertThatThrownBy(() -> filter.doFilterInternal(req, new MockHttpServletResponse(), chain))
            .isInstanceOf(jakarta.servlet.ServletException.class)
            .hasMessageContaining("simulated");
    }

    // ──────────── TenantRlsListener ────────────

    @Test void rlsListenerSetsSessionVarWhenTenantBound() throws Exception {
        TenantRlsListener listener = new TenantRlsListener();
        UUID tenantId = UUID.randomUUID();

        java.sql.PreparedStatement stmt = mock(java.sql.PreparedStatement.class);
        // The listener calls conn.unwrap(Connection.class) to get the raw connection;
        // the mock returns itself since it IS the raw connection.
        Connection conn = mock(Connection.class);
        when(conn.unwrap(Connection.class)).thenReturn(conn);
        when(conn.prepareStatement(anyString())).thenReturn(stmt);

        ExecuteContext ctx = mock(ExecuteContext.class);
        when(ctx.connection()).thenReturn(conn);

        ScopedValue.where(TenantContext.CURRENT, tenantId).run(() -> listener.executeStart(ctx));

        verify(conn).unwrap(Connection.class);
        verify(conn).prepareStatement(anyString());
        verify(stmt).execute();
    }

    @Test void rlsListenerSkipsWhenTenantNotBound() {
        TenantRlsListener listener = new TenantRlsListener();
        ExecuteContext ctx = mock(ExecuteContext.class);

        // TenantContext.CURRENT is not bound — listener must exit without touching connection
        listener.executeStart(ctx);

        verify(ctx, never()).connection();
    }
}
