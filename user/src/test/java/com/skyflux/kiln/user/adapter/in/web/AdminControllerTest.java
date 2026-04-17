package com.skyflux.kiln.user.adapter.in.web;

import com.skyflux.kiln.user.application.port.in.AuthenticateUserUseCase;
import com.skyflux.kiln.user.application.port.in.CountUsersUseCase;
import com.skyflux.kiln.user.application.port.in.GetUserUseCase;
import com.skyflux.kiln.user.application.port.in.RegisterUserUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link AdminController}.
 *
 * <p>Phase 4.2 demo surface for the RBAC stack. Note: {@code @SaCheckRole("ADMIN")}
 * is enforced by Sa-Token's AOP (loaded by the {@code auth} module at app-level),
 * which is NOT wired into the {@code @WebMvcTest} slice context. That means this
 * slice cannot assert the 403 unauthorized path — the full auth flow (login →
 * role check → 403 vs 200) is covered by {@code KilnIntegrationTest} in the
 * {@code app} module.
 *
 * <p>Scope of this slice:
 * <ul>
 *   <li>Controller wires the {@link CountUsersUseCase#execute()} call through.</li>
 *   <li>Response DTO shape is pinned to {@code {"count": N}} only.</li>
 * </ul>
 */
@WebMvcTest(AdminController.class)
class AdminControllerTest {

    /** Minimal boot config so {@code @WebMvcTest} finds a {@code @SpringBootConfiguration}. */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(basePackageClasses = AdminController.class)
    static class BootConfig {
    }

    @MockitoBean
    CountUsersUseCase countUsers;

    // Same package → UserController + AuthController are auto-scanned. Their
    // constructor-injected dependencies need stubs so the slice context loads.
    @MockitoBean
    GetUserUseCase getUserUseCase;
    @MockitoBean
    RegisterUserUseCase registerUseCase;
    @MockitoBean
    AuthenticateUserUseCase authenticateUseCase;

    @Autowired
    MockMvc mvc;

    @Test
    void count_returns_zero_when_no_users() throws Exception {
        when(countUsers.execute()).thenReturn(0L);

        mvc.perform(get("/api/v1/admin/users/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void count_returns_positive_value() throws Exception {
        when(countUsers.execute()).thenReturn(42L);

        mvc.perform(get("/api/v1/admin/users/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(42));
    }

    @Test
    void count_response_only_exposes_count() throws Exception {
        when(countUsers.execute()).thenReturn(7L);

        mvc.perform(get("/api/v1/admin/users/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").exists())
                // DTO shape pin — no extra leaked fields
                .andExpect(jsonPath("$.users").doesNotExist())
                .andExpect(jsonPath("$.total").doesNotExist())
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
