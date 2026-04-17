package com.skyflux.kiln.user.adapter.in.web;

import cn.dev33.satoken.stp.StpUtil;
import com.skyflux.kiln.user.application.port.in.AuthenticateUserUseCase;
import com.skyflux.kiln.user.application.port.in.GetUserUseCase;
import com.skyflux.kiln.user.application.port.in.RegisterUserUseCase;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link AuthController}.
 *
 * <p>Phase 4 Gate 3 remediation (review finding C2): until this test existed,
 * the auth login / logout HTTP contract was only covered by
 * {@code KilnIntegrationTest}, leaving slice-level concerns — request body
 * validation, DTO shape, logout dispatch to {@link StpUtil#logout} — untested.
 */
@WebMvcTest(AuthController.class)
class AuthControllerTest {

    /** Minimal boot config so {@code @WebMvcTest} finds a {@code @SpringBootConfiguration}. */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(basePackageClasses = AuthController.class)
    static class BootConfig {
    }

    @MockitoBean
    AuthenticateUserUseCase authenticateUseCase;

    // Auto-scan also picks up UserController (same package). It constructor-injects
    // GetUserUseCase + RegisterUserUseCase — supply stubs so the context loads.
    @MockitoBean
    GetUserUseCase getUserUseCase;
    @MockitoBean
    RegisterUserUseCase registerUseCase;

    @Autowired
    MockMvc mvc;

    @Test
    void login_returns_200_with_token() throws Exception {
        when(authenticateUseCase.execute(any(AuthenticateUserUseCase.Command.class)))
                .thenReturn("opaque-token-value");

        String body = """
                {"email":"alice@example.com","password":"S3cret-pw"}
                """;

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("opaque-token-value"));
    }

    @Test
    void login_rejects_blank_email_with_400() throws Exception {
        String body = """
                {"email":"","password":"S3cret-pw"}
                """;

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_rejects_malformed_email_with_400() throws Exception {
        String body = """
                {"email":"not-an-email","password":"S3cret-pw"}
                """;

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_rejects_blank_password_with_400() throws Exception {
        String body = """
                {"email":"alice@example.com","password":""}
                """;

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_rejects_oversize_password_with_400() throws Exception {
        // C1: 129-char password exceeds @Size(max = 128) — Argon2id-DoS guard.
        String big = "a".repeat(129);
        String body = """
                {"email":"alice@example.com","password":"%s"}
                """.formatted(big);

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logout_returns_200_and_invokes_StpUtil_logout() throws Exception {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            mvc.perform(post("/api/v1/auth/logout"))
                    .andExpect(status().isOk());

            stp.verify(StpUtil::logout, times(1));
        }
    }
}
