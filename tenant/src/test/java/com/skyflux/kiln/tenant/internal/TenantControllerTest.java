package com.skyflux.kiln.tenant.internal;

import com.skyflux.kiln.tenant.api.TenantId;
import com.skyflux.kiln.tenant.domain.Tenant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for {@link TenantController}.
 *
 * <p>Sa-Token's {@code @SaCheckRole} AOP is not loaded in the WebMvcTest slice,
 * so these tests exercise the controller's request mapping, validation, and
 * service delegation without the auth gate.
 */
@WebMvcTest(TenantController.class)
class TenantControllerTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(basePackageClasses = TenantController.class)
    static class BootConfig {}

    @MockitoBean
    TenantService service;

    @Autowired
    MockMvc mvc;

    private static Tenant sampleTenant(String code) {
        return new Tenant(TenantId.of(UUID.randomUUID().toString()),
                code, "Sample Tenant", "ACTIVE", Instant.now());
    }

    @Test
    void post_creates_tenant_and_returns_201() throws Exception {
        when(service.create(eq("acme"), any())).thenReturn(sampleTenant("acme"));

        mvc.perform(post("/api/v1/admin/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"acme","name":"Acme Corp"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("acme"));
    }

    @Test
    void post_rejects_invalid_tenant_code_with_400() throws Exception {
        mvc.perform(post("/api/v1/admin/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"INVALID CODE!","name":"Bad"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_returns_tenant() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(any(TenantId.class))).thenReturn(sampleTenant("acme"));

        mvc.perform(get("/api/v1/admin/tenants/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("acme"));
    }

    @Test
    void put_updates_tenant() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.update(any(TenantId.class), any(), any()))
                .thenReturn(sampleTenant("acme"));

        mvc.perform(put("/api/v1/admin/tenants/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated","status":"ACTIVE"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void delete_suspends_tenant_and_returns_204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(service).suspend(any(TenantId.class));

        mvc.perform(delete("/api/v1/admin/tenants/{id}", id))
                .andExpect(status().isNoContent());
    }
}
