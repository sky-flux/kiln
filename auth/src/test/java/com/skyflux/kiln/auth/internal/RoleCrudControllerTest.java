package com.skyflux.kiln.auth.internal;

import com.skyflux.kiln.auth.domain.Role;
import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.infra.web.GlobalExceptionHandler;
import com.skyflux.kiln.infra.web.ResponseBodyWrapAdvice;
import com.skyflux.kiln.tenant.api.TenantContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link RoleCrudController}.
 *
 * <p>The {@code @SaCheckRole("ADMIN")} AOP guard is not loaded into the
 * {@code @WebMvcTest} slice context — the auth path is covered by
 * {@code KilnIntegrationTest} in Wave 3. This slice pins HTTP verb/path
 * wiring, delegation, and request/response shapes.
 *
 * <p>A {@link TenantContextFilter} is registered on the {@link MockMvc}
 * instance so every request has {@link TenantContext#CURRENT} bound —
 * mirroring what {@code TenantFilter} does in production.
 */
@WebMvcTest(RoleCrudController.class)
class RoleCrudControllerTest {

    static final UUID SYSTEM_TENANT_ID = UUID.fromString("00000000-0000-7000-8000-000000000001");

    /**
     * A Servlet filter that binds {@link TenantContext#CURRENT} for each
     * MockMvc request — simulates what {@code TenantFilter} does in production.
     */
    static class TenantContextFilter implements Filter {
        private final UUID tenantId;

        TenantContextFilter(UUID tenantId) {
            this.tenantId = tenantId;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            try {
                ScopedValue.where(TenantContext.CURRENT, tenantId).run(() -> {
                    try {
                        chain.doFilter(request, response);
                    } catch (IOException | ServletException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (RuntimeException e) {
                if (e.getCause() instanceof IOException io) throw io;
                if (e.getCause() instanceof ServletException se) throw se;
                throw e;
            }
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(
            basePackageClasses = RoleCrudController.class,
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = RoleCrudController.class))
    @Import({GlobalExceptionHandler.class, ResponseBodyWrapAdvice.class})
    static class BootConfig {
    }

    @MockitoBean
    RoleCrudService roleCrudService;

    @Autowired
    WebApplicationContext wac;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilter(new TenantContextFilter(SYSTEM_TENANT_ID))
                .build();
    }

    @Test
    void createRole_returns201_withRoleBody() throws Exception {
        UUID tenantId = UUID.fromString("00000000-0000-7000-8000-000000000001");
        UUID roleId = Ids.next();
        Role created = new Role(roleId, "EDITOR", "Editor", tenantId);
        when(roleCrudService.createRole(any(), eq("EDITOR"), eq("Editor"))).thenReturn(created);

        mvc.perform(post("/api/v1/admin/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"EDITOR","name":"Editor"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("EDITOR"))
                .andExpect(jsonPath("$.data.name").value("Editor"));
    }

    @Test
    void listRoles_returns200_withRoleList() throws Exception {
        UUID tenantId = UUID.fromString("00000000-0000-7000-8000-000000000001");
        List<Role> roles = List.of(
                new Role(Ids.next(), "ADMIN", "Administrator", tenantId),
                new Role(Ids.next(), "USER", "Regular user", tenantId));
        when(roleCrudService.listRoles()).thenReturn(roles);

        mvc.perform(get("/api/v1/admin/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].code").value("ADMIN"))
                .andExpect(jsonPath("$.data[1].code").value("USER"));
    }

    @Test
    void deleteRole_returns204() throws Exception {
        UUID roleId = Ids.next();
        doNothing().when(roleCrudService).deleteRole(roleId);

        mvc.perform(delete("/api/v1/admin/roles/{id}", roleId))
                .andExpect(status().isNoContent());

        verify(roleCrudService).deleteRole(roleId);
    }

    @Test
    void createRole_withBlankCode_returns400() throws Exception {
        mvc.perform(post("/api/v1/admin/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"","name":"Editor"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
