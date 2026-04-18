package com.skyflux.kiln.dict.internal;

import com.skyflux.kiln.dict.domain.DictType;
import com.skyflux.kiln.tenant.api.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link DictTypeController}.
 *
 * <p>Sa-Token's {@code @SaCheckRole} AOP is not loaded in the WebMvcTest slice,
 * so these tests exercise the controller's request mapping, validation, and
 * service delegation without the auth gate.
 */
@WebMvcTest(DictTypeController.class)
class DictTypeControllerTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(
        basePackageClasses = DictTypeController.class,
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = DictTypeController.class),
        useDefaultFilters = false
    )
    static class BootConfig {

        /** Binds a test tenant UUID so {@link TenantContext#CURRENT} is available per-request. */
        @Bean
        OncePerRequestFilter tenantContextFilter() {
            return new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest request,
                                                HttpServletResponse response,
                                                FilterChain filterChain)
                        throws ServletException, IOException {
                    try {
                        ScopedValue.where(TenantContext.CURRENT, UUID.randomUUID())
                                .call(() -> { filterChain.doFilter(request, response); return null; });
                    } catch (Exception e) {
                        throw new ServletException(e);
                    }
                }
            };
        }
    }

    @MockitoBean
    DictService service;

    @Autowired
    MockMvc mvc;

    private static DictType sampleType(String code) {
        return new DictType(UUID.randomUUID(), code, "Sample " + code, false,
                UUID.randomUUID(), null);
    }

    @Test
    void get_types_returns_200_with_array() throws Exception {
        when(service.listTypes()).thenReturn(List.of(sampleType("GENDER"), sampleType("STATUS")));

        mvc.perform(get("/api/v1/dict/types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void post_creates_type_and_returns_201() throws Exception {
        when(service.createType(eq("GENDER"), eq("Gender"), any(UUID.class)))
                .thenReturn(sampleType("GENDER"));

        mvc.perform(post("/api/v1/admin/dict/types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"GENDER","name":"Gender"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("GENDER"));
    }

    @Test
    void post_rejects_lowercase_code_with_400() throws Exception {
        mvc.perform(post("/api/v1/admin/dict/types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"gender","name":"Gender"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
